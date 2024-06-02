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
//   - #include wesmc.lUtils

library(
  name: 'lPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

//// ------------------------------------------
//// UI SUPPORT FOR SOLICITING PBSG CONFIG DATA
//// ------------------------------------------

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

Map gatherPbsgStateFromConfig(String pbsgName, String instType = 'pbsg') {
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

//// ------------------------------------
//// CONVENIENCE METHODS ON PBSG INSTANCE
//// ------------------------------------

String switchState(DevW d) {
  return d.currentValue('switch', true) // true -> Do not use the cache
}

//// --------------------------------------------------------------
//// OPERATIONS ON A PBSG INSTANCE (a Map persisted to AtomicState)
//// --------------------------------------------------------------

Map getPbsgState(String pbsgName) {
  return atomicState."${pbsgName}"
}

void putPbsgState(Map pbsg) {
  // Save the PBSG to atomicState
  atomicState."${pbsg.name}" = pbsg
  // Ensure child VSWs are on|off consistent with the PBSG state
  pbsg_ReconcileVswsToState(pbsg)
  // Invoke the client's callback function to consume the latest changes.
  pbsg_ButtonOnCallback(pbsg)
}

boolean pbsg_ActivateButton(Map pbsg, String button) {
  // Assumed: pbsg != null
  // Returns true on a PBSG state change
  boolean result = false
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
      "Ignoring the requested deactivation of default button ${b(button)}"
    )
  } else if (pbsg.active == button) {
    if (pbsg.dflt) {
      // Swap currentlt active button with default button
      logTrace('pbsg_Deactivate', "Activating default ${b(pbsg.dflt)}")
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
  // Device Network Identifiers DO NOT include white space.
  // Device Names (exposed to Alexa ...) DO NOT include special characters.
  DevW d = getChildDevice(dni)
  if (!d) {
    logWarn('getOrCreateDevice', "Creating VSW ${b(dni)}")
    d = addChildDevice(
      'hubitat',         // namespace
      'Virtual Switch',  // typeName
      dni,               // Device Network Identifier (<pbsgName>_<button>)
      [                  // properties
        isComponent: true,
        name: dni.replaceAll('_', ' ')  // "<pbsgName> <button>"
      ]
    )
  }
  return d
}

void pbsg_ReconcileVswsToState(Map pbsg) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state.
  // Get or Create the child VSW for each button.
  Map buttonToVsw = [:]
  pbsg?.all.each{ button ->
    String dni = "${pbsg.name}_${button}"
    buttonToVsw << [ (button) : getOrCreateDevice(dni) ]
  }
  //---> // Unsubscribe from child VSW events.
  //---> buttonToVsw.each{ button, vsw -> unsubscribe(vsw) }
  // Ensure VSW state matches button state.
  buttonToVsw.each{ button, vsw ->
    if (pbsg.active == button) {
      if ( switchState(vsw) != 'on') { vsw.on() }
    } else {
      if ( switchState(vsw) != 'off') { vsw.off() }
    }
  }
  pauseExecution(100) // Pause for child vsw updates.
  //---> // Re-subscribe to child VSW events.
  //---> buttonsToVsw.each{ button, vsw ->
  //--->   subscribe(vsw, 'switch', PbsgChildVswEventHandler, ['filterEvents': true])
  //---> }
//  pauseExecution(50) // Pause for re-subscription.
}

boolean pbsg_ActivateLastActive(Map pbsg) {
  // Assumed: pbsg != null
  // -----------------------------------------------------------------
  // I M P O R T A N T   (circa Q2'24)
  //   The Groovy ListArray implementation operates in reverse order
  //     - push(item) APPENDS the item to [] instead of PREPENDING it
  //     - pop() retrieves the last item pushed, at 'lifo.size() - 1'
  //       rather than position '0' per current Groovy docs.
  // -----------------------------------------------------------------
  Integer latestPushIndex = pbsg.lifo.size() - 1
  return pbsg_ActivateButton(pbsg, pbsg.lifo[latestPushIndex])
}

boolean pbsg_EnforceDefault(Map pbsg) {
  // Assumed: pbsg != null
  // Returns true on a PBSG state change
  boolean result = false
  if (pbsg && !pbsg.active && pbsg.dflt) {
    logInfo('pbsg_EnforceDefault', "Activating default ${b(pbsg.dflt)}")
    result = pbsg_ActivateButton(pbsg, pbsg.dflt)
  }
  return result
}

Map pbsg_BuildToConfig(Map pbsg) {
  // This method (re-)builds a PBSG and writes it to atomicState
  // Parameter Assumptions:
  //   The supplied Map (pbsg) IS NOT null and includes:
  //     - pbsg.name which IS NOT null
  //     - pbsg.all which IS NOT null and IS an ArrayList of button names
  pbsg.lifo = []
  // Process pbsg.all buttons and populate pbsg.lifo and pbsg.active
  // based on the current state of any discovered VSWs.
  pbsg.all.each { button ->
    if (button) {
      String dni = "${pbsg.name}_${button}"
      DevW vsw = getOrCreateDevice(dni)
      pbsg.lifo.push(button)
      if (switchState(vsw) == 'on') {
        // Move the button from the LIFO to active
        logInfo('pbsg_BuildToConfig', "Found VSW ${dni} (${b('on')})")
        pbsg_ActivateButton(pbsg, button)
      } else {
        logInfo('pbsg_BuildToConfig', "VSW ${dni} (${i('off')})")
      }
      subscribe(vsw, 'switch', PbsgChildVswEventHandler, ['filterEvents': true])
    } else {
      logError('pbsg_BuildToConfig', "Encountered a null in pbsg.all (${pbsg.all})")
    }
  }
  if (!pbsg.active) { pbsg_EnforceDefault(pbsg) }
  putPbsgState(pbsg)  // Save to atomicState and issue client callback
  return pbsg
}

String pbsg_ButtonState(Map pbsg, String button) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state.
  if (button != null) {
    String tag = (button == pbsg.dflt) ? '*' : ''
    String summary = "${tag}<b>${button}</b> "
    DevW vsw = getChildDevice("${pbsg.name}_${button}")
    String swState = switchState(vsw)
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

//// PBSG EVENT HANDLER

void PbsgChildVswEventHandler(Event e) {
  // VERY IMPORTANT
  //   - VSW event subscriptions are suppressed when this application is
  //     adjusting its owned VSWs.
  //   - This handler exists to processes VSW changes made externally
  //     (e.g., by the Hubitat GUI, Alexa, ...).
  //   - An event's displayName (e.displayName) is the "name" of the VSW,
  //     NOT the DNI of the VSW.
  //         Format of name: '${pbsgInstName} ${buttonName}'
  //         Format of dni:  '${pbsgInstName}_${buttonName}'
  //     Take care when tokenizing e.displayName.
  //   - RA2 turns off one scene BEFORE turning on the replacement scene.
  //   - PRO2 turns on scenes without turning off predecessors.
  //   - Ensure that PBSG adjustements are persisted to atomicState.
  //--->
  //---> logInfo('PbsgChildVswEventHandler', "${e.displayName} → ${e.value}")
  if (e.name == 'switch') {
    ArrayList parsedName = e.displayName.tokenize(' ')
    String pbsgName = parsedName[0]
    String button = parsedName[1]
    Map pbsg = getPbsgState(pbsgName)
    switch (e.value) {
      case 'on':
        if (pbsg.active != button) {
          // This reported event DOES NOT match the current PBSG state.
          logInfo(
            'PbsgChildVswEventHandler',
            "${b(button)} VSW turned on .. activating")
          pbsg_ActivateButton(pbsg, button) && putPbsgState(pbsg)
        } else {
          logTrace('PbsgChildVswEventHandler', "Ignoring ${b(button)} turned on")
        }
        break
      case 'off':
        if (!pbsg.lifo.contains(button)) {
          // This reported event DOES NOT match the current PBSG state.
          logInfo(
            'PbsgChildVswEventHandler',
            "${b(button)} VSW turned off .. deactivating")
          pbsg_DeactivateButton(pbsg, button) && putPbsgState(pbsg)
        } else {
          logTrace('PbsgChildVswEventHandler', "Ignoring ${b(button)} turned off")
        }
        break
      default:
        logError('PbsgChildVswEventHandler', ['',
          e.descriptionText,
          "Unexpected e.value: ${b(e.value)}",
          eventDetails(e)
        ].join('<br/>'))
    }
  } else {
    logTrace(
      'PbsgChildVswEventHandler',
      "Ignoring ${eventDetails(e)}"
    )
  }
  //---> logInfo('PbsgChildVswEventHandler', "pbsgName: ${pbsgName}, button: ${button}")
  //---> if (e.value == 'on') {
  //--->   pbsg_ActivateButton(pbsg, button) && putPbsgState(pbsg)
  //--->   //-> logInfo('PbsgChildVswEventHandler', pbsg_State(pbsg))
  //---> } else if (e.value == 'off') {
  //--->   pbsg_DeactivateButton(pbsg, button) && putPbsgState(pbsg)
  //--->   //-> logInfo('PbsgChildVswEventHandler', pbsg_State(pbsg))
  //---> } else {
  //--->   logWarn(
  //--->     'PbsgChildVswEventHandler',
  //--->     "Unexpected value (${e.value}) for (${e.displayName}"
  //--->   )
  //--->}
}
