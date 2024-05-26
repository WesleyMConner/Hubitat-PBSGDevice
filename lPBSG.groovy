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
// The following are required when using this library.
//   - //   - #include wesmc.lUtils

library(
  name: 'lPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

ArrayList buttonsAsList(String buttonsSettingsKey) {
  String buttons = settings."${buttonsSettingsKey}"
  return buttons?.tokenize(' ')
}

void solicitPbsgConfig(String pbsgName) {
  // OVERVIEW
  //   - This method is called from within a page section{...}
  //   - The client first populates the names of the PBSG buttons.
  //   - Once the list of buttons exists, the user selects a default button
  //     or 'not applicable'
  //   - All exposed fields can be edited until data entry is "Done".
  paragraph(h1("Provide '${b(pbsgName)}' Configuration Data"))
  // Tokenize the space-delimited buttons to produdce a list of buttons
  ArrayList buttonList = buttonsAsList("${pbsgName}_buttonsSettingsKey")
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
    name: "${pbsgName}_buttonsSettingsKey",
    title: heading.join('<br/>'),
    type: 'text',
    submitOnChange: true,
    required: true,
    multiple: false
  )
  if (buttonCount > 2) {
    input(
      name: "${pbsgName}_DfltSettingsKey",
      title: "Default Button for ${b(pbsgName)}:",
      type: 'enum',
      submitOnChange: true,
      required: true,                 // Ensures user makes a selection!
      multiple: false,
      options: [*buttonList, 'not applicable']
    )
  }
}

void convertSolicitedPbsgConfigToState(String pbsgName, String instType = 'pbsg') {
  // The client was forced to populate a default PBSG button.
  // If 'not applicable' was selected, coerce it to null.
  String dfltSettingsKey = "${pbsgName}_DfltSettingsKey"
  //-> String defaultButton = settings."${dfltSettingsKey}"
  //-> if (defaultButton == 'not applicable') { defaultButton == null }
  // Assemble the PBSG Config Map in state (as seed data for creating a
  // full-fledged PBSG).
  atomicState."${pbsgName}" = [
    name: pbsgName,
    instType: instType,
    allButtons: buttonsAsList("${pbsgName}_buttonsSettingsKey"),
    defaultButton: (settings."${dfltSettingsKey}" == 'not applicable')
      ? null
      : settings."${dfltSettingsKey}"
  ]
}

DevW pbsg_GetOrCreateMissingDevice(DevW device, String deviceDNI) {
  if (device && device.deviceNetworkId != deviceDNI) {
    logError(
      'pbsg_GetOrCreateMissingDevice',
      "Provided device DNI (${device.deviceNetworkId}) != deviceDNI (${deviceDNI})"
    )
  }
  Map addChildArgs = [
    isComponent: true,
    name: deviceDNI.replaceAll('_', ' ')
  ]
  DevW d = device
  ?: getChildDevice(deviceDNI)
  ?: addChildDevice(
    'hubitat',          // namespace
    'Virtual Switch',   // typeName
    deviceDNI,          // device's unique DNI (with '_' delimiter)
    addChildArgs
  )
  return d
}

void pbsg_ActivateButton(String pbsgName, String button, DevW device = null) {
  //-> logInfo('pbsg_ActivateButton#111', "pbsgName: >${pbsgName }<")
  Map pbsgMap = atomicState."${pbsgName}"
  if (pbsgMap) {
    DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsgMap.name}_${button}")
    String dState = switchState(d)
    if (pbsg?.activeButton == button) {
      // The button is already active. Ensure its VSW is 'on'.
      if (dState != 'on') {
        logWarn('pbsg_ActivateButton', "Correcting ACTIVE ${button} w/ state '${dState}'")
        unsubscribe(d)
        d.on()
        pbsg_ButtonOnCallback(pbsgName)
        pauseExecution(100)                // Pause for downstream changes
        subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
      }
    } else {
      if (pbsgMap?.activeButton != null) {
        // IMPORTANT: Move the currently active button out of the way, but
        //            DO NOT leverage pbsg_DeactivateButton() which will
        //            populate an empty activeButton with defaultButton.
        DevW priorActive = pbsg_GetOrCreateMissingDevice(
          null,
          "${pbsgMap.name}_${pbsgMap.activeButton}"
        )
        unsubscribe(priorActive)
        priorActive.off()
        pbsgMap.buttonsLIFO.push(pbsgMap.activeButton)
        pbsgMap.activeButton = null
        atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
        //-> pbsgStore_Save(pbsgMap)
        pauseExecution(100)                // Pause for downstream changes
        subscribe(priorActive, pbsg_VswEventHandler, ['filterEvents': true])
      }
      // Move the newly-activated button from the LIFO to the active slot.
      Integer indexInLIFO = null
      pbsgMap.buttonsLIFO.eachWithIndex { b, i ->
        if (b == button) { indexInLIFO = i }
      }
      if (indexInLIFO) {
        pbsgMap.buttonsLIFO.removeAt(indexInLIFO)
      }
      unsubscribe(d)
      pbsgMap.activeButton = button
      d.on()
      atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
      //-> pbsgStore_Save(pbsgMap)
      pbsg_ButtonOnCallback(pbsgName)       // Trigger button 'on' behavior
      pauseExecution(100)                  // Pause for downstream changes
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    }
  } else {
    logError('pbsg_ActivateButton', "Cannot find pbsg '${pbsgName}'")
  }
}

void pbsg_DeactivateButton(String pbsgName, String button, DevW device = null) {
  Map pbsgMap = atomicState."${pbsgName}"
  if (pbsgMap) {
    DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsgMap.name}_${button}")
    if (pbsgMap.activeButton == button) {
      unsubscribe(d)
      d.off()
      pbsgMap.buttonsLIFO.push(pbsgMap.activeButton)
      pbsgMap.activeButton = null
      atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
      //-> pbsgStore_Save(pbsgMap)
      pauseExecution(100)                    // Pause for downstream changes
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
      pbsg_EnforceDefault(pbsgName)
    } else if (!pbsgMap.buttonsLIFO.contains(button)) {
      //-> logInfo('pbsg_DeactivateButton', "Adding button '${button}' → ${pbsg.name} ${pbsg.buttonsLIFO}")
      unsubscribe(d)
      d.off()
      pbsgMap.buttonsLIFO.push(button)
      atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
      //-> pbsgStore_Save(pbsgMap)
      pauseExecution(100)                    // Pause for downstream changes
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    }
  // Else: Nothing to do, button is already deactivated
  } else {
    logWarn('pbsg_DeactivateButton', "Cannot find pbsg '${pbsgName}'")
  }
}

void pbsg_EnforceDefault(String pbsgName) {
  if (atomicState."${pbsgName}") {
    if (
      !atomicState."${pbsgName}".activeButton
      && atomicState."${pbsgName}".defaultButton
    ) {
      logInfo('pbsg_EnforceDefault', "Target is '${pbsgName}'")
      pbsg_ActivateButton(pbsgName, atomicState."${pbsgName}".defaultButton)
    }
  } else {
    logWarn('pbsg_EnforceDefault', "Cannot find pbsg '${pbsgName}'")
  }
}

void missingKey(String stateKey, String objectKey) {
  logError('missingKey', "Cannot find objectKey '${objectKey}' for stateKey '${stateKey}'")
}

Boolean pbsgConfigExists(String pbsgName) {
  // PBSG creation leverages the following initial 'configuration' fields:
  //   - name ............. required
  //   - instType ......... required ('pbsg' is the defacto "base class")
  //   - allButtons ....... required
  //   - defaultButton .... optional
  Boolean result = false
  if (!atomicState."${pbsgName}") {
    logError('pbsgConfigExists', "Cannot find '${pbsgName}' in atomicState")
  } else if (!atomicState."${pbsgName}".name) {
    missingKey(pbsgName, 'name')
  } else if (!atomicState."${pbsgName}".instType) {
    missingKey(pbsgName, 'instType')
  } else if (!atomicState."${pbsgName}".allButtons) {
    missingKey(pbsgName, 'allButtons')
  } else {
    result = true
  }
  return result
}

void pbsg_BuildToConfig(String pbsgName) {
  if (pbsgConfigExists(pbsgName)) {
    // Initialize PBSG instance fields
    atomicState."${pbsgName}".buttonsLIFO = []
    atomicState."${pbsgName}".activeButton = null
    // Process buttons to locate or create associated VSWs. The state of
    // existing devices is respected to the extent that a single PBSG
    // VSW is active.
    atomicState."${pbsgName}".allButtons.each { button ->
      String deviceDni = "${pbsgName}_${button}"
      DevW device = getChildDevice(deviceDni)
      switch (switchState(device)) {
        case 'on':
          pbsg_ActivateButton(pbsgName, button)
          break
        default:
          pbsg_DeactivateButton(pbsgName, button)
      }
    }
    if (!atomicState."${pbsgName}".activeButton) {
      pbsg_EnforceDefault(pbsgName)
    }
    getChildDevices().each { device ->
      String dni = device.deviceNetworkId
      ArrayList dniNameAndButton = dni.tokenize('_')
      String dniPBSGName = dniNameAndButton[0]
      String dniButtonName = dniNameAndButton[1]
      if (
        pbsgName == dniPBSGName
        && !atomicState."${pbsgName}"?.allButtons.contains(dniButtonName)) {
        logWarn('pbsg_BuildToConfig', "Deleting orphaned VSW '${dni}'")
        deleteChildDevice(dni)
      }
    }
  }
}

void pbsg_VswEventHandler(Event e) {
  // VERY IMPORTANT
  //   - VSW event subscriptions are suppressed when this application is
  //     adjusts VSWs (e.g., in response to Lutron RA2 and Pro2 events).\
  //   - This handler processes events that arise from external actions
  //     (e.g., Hubitat GUI, Alexa).
  //   - An event's displayName is the "name" of the VSW, not the DNI.
  //       Format of name: '${pbsgInstName} ${buttonName}'
  //       Format of DNI:  '${pbsgInstName}_${buttonName}'
  //     So, care must be taken when tokenizing e.displayName.
  //   - RA2 turns off one scene BEFORE turning on the replacement scene.
  //   - PRO2 turns on scenes without turning off predecessors.
  //
  logInfo('pbsg_VswEventHandler', "${e.displayName} → ${e.value}")
  ArrayList parsedDNI = e.displayName.tokenize(' ')
  String pbsgName = parsedDNI[0]
  String button = parsedDNI[1]
  if (e.value == 'on') {
    pbsg_ActivateButton(pbsgName, button)
  } else if (e.value == 'off') {
    pbsg_DeactivateButton(pbsgName, button)
  } else {
    logWarn(
      'pbsg_VswEventHandler',
      "Unexpected value (${e.value}) for DNI (${dni}")
  }
}

String buttonState(String pbsgName, String button) {
  Map pbsgMap = atomicState."${pbsgName}"
  if (button != null) {
    String tag = (button == pbsgMap.defaultButton) ? '*' : ''
    String summary = "${tag}<b>${button}</b> "
    DevW device = getChildDevice("${pbsgName}_${button}")
    String swState = switchState(device)
      ?: logError('buttonState', "switchState() failed for button (${button}).")
    if (swState == 'on') {
      summary += '(<b>on</b>)'
    } else if (swState == 'off') {
      summary += '(<em>off</em>)'
    } else {
      summary += '(--)'
    }
  } else {
    logError('buttonState', 'button arg is NULL')
  }
}

String pbsg_State(String pbsgName) {
  String result
  if (atomicState."${pbsgName}") {
    result = "PBSG \"<b>${atomicState."${pbsgName}".name}</b>\" "
    result += '['
    result += atomicState."${pbsgName}".buttonsLIFO.collect { button ->
      buttonState(pbsgName, button)
    }.join(', ')
    result += ']'
    String activeButton = atomicState."${pbsgName}".activeButton
    result += (activeButton) ? " → ${buttonState(pbsgName, activeButton)}" : ''
  } else {
    logError('pbsg_State', "Could not find pbsg '${pbsgName}'")
  }
  return result
}

void pbsg_ButtonOnCallback(String pbsgName) {
  // Once a PBSG instance is created, this method is called to communicate
  // a change in the state of the named PBSG instance.
  logInfo('pbsg_ButtonOnCallback', "pbsg: ${pbsg_State(pbsgName)}")
}
