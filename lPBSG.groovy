// ---------------------------------------------------------------------------------
// P ( U S H )   B ( U T T O N )   S ( W I T C H )   G ( R O U P )
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
// Referenced types below
//   - import com.hubitat.app.DeviceWrapper as DevW
//   - import com.hubitat.hub.domain.Event as Event
//   - import java.util.ArrayList
// The following are required when using this library.
//   - //   - #include wesmc.lUtils


library(
  name: 'lPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

// UI SUPPORT FOR SOLICITING PBSG CONFIG DATA

ArrayList buttonStringToButtonList(String buttonsSettingsKey) {
  String buttons = settings."${buttonsSettingsKey}"
  return buttons?.tokenize(' ')
}

void solicitPbsgConfig(String pbsgName) {
  // OVERVIEW
  //   - This method is called from within a page section{...}
  //   - The client first populates the names of the PBSG buttons.
  //   - Once the list of buttons exists, the user selects a
  //     default (dflt) button or 'not applicable'
  //   - All exposed fields can be edited until data entry is "Done".
  paragraph(h1("Provide '${b(pbsgName)}' Configuration Data"))
  // Tokenize the space-delimited buttons to produdce a list of buttons
  ArrayList buttonList = buttonStringToButtonList("${pbsgName}_allSettingsKey")
  Integer buttonCount = buttonList?.size()
  // Adjust the title (heading) to provide feedback to the client.
  ArrayList heading = []
  switch (buttonCount) {
    case 1:
      heading << h1('Oops only one button was detected')
      heading << "${b('Buttons')}: ${buttonList}, ${b('Button Count')}: ${buttonCount}"
      // Deliberately fall through to the next case!
    case null:
      heading << h2("Create at least two buttons for PBSG ${b(pbsgName)}")
      heading << i("Enter button names ${b('delimited with spaces ')}")
      break
    default:
      heading << h2("Button names for PBSG ${b(pbsgName)}")
      heading << i('button names are delimited with spaces')
  }
  input(
    name: "${pbsgName}_allSettingsKey",
    title: heading.join('<br/>'),
    type: 'text',
    submitOnChange: true,
    required: true
  )
  if (buttonCount > 2) {
    input(
      name: "${pbsgName}_dfltSettingsKey",
      title: "Default Button for ${b(pbsgName)}:",
      type: 'enum',
      submitOnChange: true,
      required: true,                 // Ensures user makes a selection!
      multiple: false,
      options: [*buttonList, 'not applicable']
    )
  }
}

// SUPPORTING METHODS ON CHILD VSWS

// METHODS ON PBSG INSTANCE

Map getPbsg(String pbsgName) {
  return atomicState."${pbsgName}"
}

void updatePbsgState(Map pbsg) {
  // Ensure the VSWs are on/off consistent with the PBSG
  pbsg_ReconcileVswState(pbsg)
  // Save the PBSG atomicState
  atomicState."${pbsg.name}" = pbsg
  // Invoke the client's callback function.
  pbsg_ButtonOnCallback(pbsg)
}

Map createPbsgStateFromConfig(String pbsgName, String instType = 'pbsg') {
  // Builds/Rebuilds a PBSG instance from soicited state AND invokes
  // pbsg_BuildToConfig(pbsg) - saving the results to atomicState.
  Map result = [:]
  if (pbsgName) {
    String allSettingsKey = "${pbsgName}_allSettingsKey"
    ArrayList allViaSettings = buttonStringToButtonList(allSettingsKey)
    String dfltViaSettings = (settings."${pbsgName}_dfltSettingsKey" == 'not applicable')
      ? null
      : settings."${pbsgName}_dfltSettingsKey"
    if (allViaSettings) {
      // A minimal PBSG configuration Map exists
      result = [
        name: pbsgName,
        all: allViaSettings,
        instType: instType,
        dflt: dfltViaSettings
      ]
      // Do warn that no default button was specified
      if (!dfltViaSettings) {
        logWarn('pbsg_CollectSolicitedConfig', 'No default button was specified')
      }
    } else {
      // Insufficient data exists for a PBSG configuration Map
      String allString = settings."${allSettingsKey}"
      logError('pbsg_CollectSolicitedConfig', [
        '',
        "Found null for ${b('all')} buttons",
        "At settings.${allSettingsKey}: >${allString}<"
      ])
      // Also warn if no default button was found
      if (!dfltViaSettings) {
        logWarn('pbsg_CollectSolicitedConfig', 'No default button was specified')
      }
    }
  }
  return result
}

boolean pbsg_ActivateButton(Map pbsg, String button) {
  // Assumed: pbsg != null
  // Returns true on a PBSG state change
  boolean result = false
  //String target = button ?: pbsg.dflt
  if (pbsg.active == button) {
    logWarn('pbsg_ActivateButton', "${b(button)} was already activated")
  } else {
    if (pbsg.active) {
      pbsg.lifo.push(pbsg.active)
      result = true
    }
    if (pbsg.lifo.contains(button)) {
      pbsg.active = button
      pbsg.lifo.removeAll([button])
      result = true
    } else {
      logError('pbsg_ActivateButton', "Unable to find button ${b(button)}")
      // The PBSG's state is NOT changed.
    }
  }
  return result
}

boolean pbsg_DeactivateButton(Map pbsg, String button) {
  // Assumed: pbsg != null
  // Returns true on a PBSG state change
  boolean result = false
  if (button == pbsg.dflt) {
    logWarn(
      'pbsg_DeactivateButton',
      "Ignore requested deactivation of the default button ${b(button)}"
    )
  } else if (pbsg.active == button) {
    if (pbsg.dflt) {
      // Swap currentlt active button with default button
      pbsg_ActivateButton(pbsg, pbsg.dflt)
    } else {
      // Deactivate active button only
      pbsg.lifo.push(pbsg.active)
    }
    result = true
  } else if (pbsg.lifo.contains(button)) {
    logWarn('pbsg_DeactivateButton', "${b(button)} was already deactivated")
  } else {
    logError('pbsg_DeactivateButton', "${b(button)} was not found")
  }
  return result
}

DevW getOrCreateDevice(String dni) {
  DevW d = getChildDevice(dni)
  if (!d) {
    logWarn('getOrCreateDevice', "Creating VSW ${b(dni)}")
    d = addChildDevice(
      'hubitat',         // namespace
      'Virtual Switch',  // typeName
      dni,               // device's unique DNI (with '_' delimiter)
      [                  // properties
        isComponent: true,
        name: dni.replaceAll('_', ' ')
      ]
    )
  }
  return d
}

void pbsg_ReconcileVswState(Map pbsg) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state.
  // Get or Create the child VSW for each button.
  Map buttonToVsw = [:]
  pbsg?.all.each{ button ->
    String dni = "${pbsg.name}_${button}"
    buttonToVsw << [ (button) : getOrCreateDevice(dni) ]
  }
  // Unsubscribe from child VSW events.
  buttonToVsw.each{ button, device -> unsubscribe(device) }
  // Ensure VSW state matches button state.
  buttonToVsw.each{ button, device ->
    if (pbsg.active == button) {
      if ( switchState(device) != 'on') { device.on() }
    } else {
      if ( switchState(device) != 'off') { device.off() }
    }
  }
  // Pause for child device updates.
  pauseExecution(200)
  // Re-subscribe to child VSW events.
  buttonsToVsw.each{ button, device ->
    subscribe(device, pbsg_VswEventHandler, ['filterEvents': true])
  }
}

boolean pbsg_ActivateLastActive(Map pbsg) {
  // Assumed: pbsg != null
  // REMINDER
  //   - The ListArray implementation operates in reverse. A call to
  //     push(item) APPENDS item instead of PREPENDING item. As a
  //     conseqeucnce last active is lifo[lifo.size()-1] INSTEAD OF
  //     lifo[0]
  Integer latestPushIndex = pbsg.lifo.size() - 1
  return pbsg_ActivateButton(pbsg, pbsg.lifo[latestPushIndex])
}

boolean pbsg_EnforceDefault(Map pbsg) {
  // Assumed: pbsg != null
  // Returns true on a PBSG state change
  boolean result = false
  if (pbsg && !pbsg.active && pbsg.dflt) {
    result = pbsg_ActivateButton(pbsg, pbsg.dflt)
  }
  return result
}

Map pbsg_BuildToConfig(Map pbsg) {
  // Assumed:
  //   - A PBSG begins with the passed pbsgConfig Map (as Map pbsg)
  //   - pbsg != null, pbsg.all != null
  // Builds/Rebuilds a PBSG and writes it to atomicState
  pbsg.lifo = new ArrayList()
  // Process pbsg.all buttons and set pbsg.lifo and pbsg.active
  // based on the current state of discovered devices.
  pbsg.all.each { button ->
    if (button) {
      String dni = "${pbsg.name}_${button}"
      DevW device = getOrCreateDevice(dni)
      pbsg.lifo.push(button)
      if (switchState(device) == 'on') {
        // Promote button from LIFO to active
        logInfo('pbsg_BuildToConfig', "Found VSW ${dni} (${b('on')})")
        pbsg_ActivateButton(pbsg, button)
      } else {
        logInfo('pbsg_BuildToConfig', "VSW ${dni} (${i('off')})")
      }
    } else {
      logError('pbsg_BuildToConfig', "Encountered a null in pbsg.all (${pbsg.all})")
    }
  }
  if (!pbsg.active) { pbsg_EnforceDefault(pbsg) }
  // Write PBSG to atomicState
  updatePbsgState(pbsg)
  // Remove stale VSWs
  getChildDevices().each { device ->
    String dni = device.deviceNetworkId
    ArrayList dniNameAndButton = dni.tokenize('_')
    String dniPBSGName = dniNameAndButton[0]
    String dniButtonName = dniNameAndButton[1]
    if (pbsgName == dniPBSGName && !pbsg.all.contains(dniButtonName)) {
      logWarn('pbsg_BuildToConfig', "Deleting orphaned VSW '${dni}'")
      deleteChildDevice(dni)
    }
  }
  return pbsg
}

void pbsg_VswEventHandler(Event e) {
  // VERY IMPORTANT
  //   - VSW event subscriptions are suppressed when this application is
  //     adjusting its owned VSWs (in response to Lutron RA2 and Pro2
  //     events) to avoid feedback loops.
  //   - This handler exists to processes VSW changes made by using the
  //     Hubitat GUI, Alexa, et al.
  //   - An event's displayName (e.displayName) is the "name" of the VSW,
  //       not the DNI of the VSW.
  //         Format of name: '${pbsgInstName} ${buttonName}'
  //         Format of DNI:  '${pbsgInstName}_${buttonName}'
  //     So, care must be taken when tokenizing e.displayName.
  //   - RA2 turns off one scene BEFORE turning on the replacement scene.
  //   - PRO2 turns on scenes without turning off predecessors.
  //
  logInfo('pbsg_VswEventHandler', "${e.displayName} → ${e.value}")
  ArrayList parsedName = e.displayName.tokenize(' ')
  String pbsgName = parsedName[0]
  String button = parsedName[1]
  Map pbsg = getPbsg(pbsgName)
  if (e.value == 'on') {
    pbsg_ActivateButton(pbsg, button)
  } else if (e.value == 'off') {
    pbsg_DeactivateButton(pbsg, button)
  } else {
    logWarn(
      'pbsg_VswEventHandler',
      "Unexpected value (${e.value}) for (${e.displayName}"
    )
  }
}

String pbsg_ButtonState(Map pbsg, String button) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state.
  if (button != null) {
    String tag = (button == pbsg.dflt) ? '*' : ''
    String summary = "${tag}<b>${button}</b> "
    DevW device = getChildDevice("${pbsg.name}_${button}")
    String swState = switchState(device)
      ?: logError('pbsg_ButtonState', "switchState() failed for button (${button}).")
    if (swState == 'on') {
      summary += '(<b>on</b>)'
    } else if (swState == 'off') {
      summary += '(<em>off</em>)'
    } else {
      logError('pbsg_ButtonState', "Encountered swState: >${swState}<")
      summary += '(--)'
    }
  } else {
    logError('pbsg_ButtonState', 'button arg is NULL')
  }
}

String pbsg_State(Map pbsg) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state.
  //   IMPORTANT:
  //     LIFO push() and pop() are supported, *BUT* pushed items are
  //     appended (NOTE PREPENDED); so, the list needs to be reverse
  //     to look like a FIFO.
  String result
  if (pbsg) {

    result = "${b(pbsg.name)}: "
    result += (pbsg.active) ? "${pbsg_ButtonState(pbsg, pbsg.active)} " : ''
    result += '← ['
    result += pbsg.lifo.reverse().collect { button -> pbsg_ButtonState(pbsg, button) }.join(', ')
    result += ']'
  } else {
    logError('pbsg_State', 'Received null PBSG parameter.')
  }
  return result
}
