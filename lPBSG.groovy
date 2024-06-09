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
//   - import groovy.json.JsonOutput
//   - import groovy.json.JsonSlurper
// The following are required when using this library.
//   - #include wesmc.lUtils

library(
  name: 'lPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

// -------
// UTILITY
// -------

String switchState(DevW d) {
  return d.currentValue('switch', true) // true -> Do not use the cache
}

String dropNonUnderscoreSpecialChars(String s) {
  return s?.replaceAll(/[\W_&&[^_ ]]/, '')
}

ArrayList extractCleanNames(String s) {
  String cleanString = dropNonUnderscoreSpecialChars(s)
  return cleanString?.tokenize(' ')
}

// -----------------------------------------------
// UI DATA SOLICITATION AND OPERATIONS ON SETTINGS
// -----------------------------------------------
//
//   +------------+-----------------------+-----------------------|
//   |    CONTEXT |          App          |        Device         |
//   +------------|-----------------------+-----------------------|
//   | WRITE DATA |      via input()      |    via sendEvent()    |
//   +------------------------------------+-----------------------|
//   |  READ DATA |             via settings."${key}"             |
//   +------------+-----------------------+-----------------------|
//   |   prefix = |    Populated with     |         null          |
//   |            |    "${PbsgName}_"     |                       |
//   +------------+-----------------------+-----------------------|
//   |  dynamic = |      (device?.getName()) ? false : true       |
//   |            |    [dynamic only applies in an App context]   |
//   +------------+-----------------------+-----------------------|

Boolean isDynamic() {
  return (device?.getName()) ? false : true
}

void solicitPbsgNames() {
  input(
    name: 'pbsgNames',
    title: [
      h3('Enter one or more PBSG Names'),
      '- Separate names with spaces',
      "- Avoid special characters in names, but '_' is allowed",
      '- The names will used to create PBSG devices',
      '- Use ⏎ to save your entry '
    ].join('<br/>'),
    type: 'text',
    required: true,
    submitOnChange: true
  )
}

ArrayList getPbsgNames() {
  //String raw = settings.pbsgNames
  //return raw?.tokenize(' ')
  //-> return raw?.tokenize(' ')
  //-> logInfo('getPbsgNames#A', "raw: ${raw}")
  //-> String clean = dropNonUnderscoreSpecialChars(raw)
  //-> logInfo('getPbsgNames#B', "clean: ${clean}")
  return extractCleanNames(settings.pbsgNames)
}

void solicitButtonNames(String prefix = null) {
  String header = "${b('Button Names')} (space delimited)"
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}buttons",
    title: "${h3(header)}",
    type: 'text',
    required: true,
    submitOnChange: dynamic
  )
}

ArrayList getButtonNames(String prefix = null) {
  // Tokenize the space-delimited buttons to produce the ArrayList
  String buttonNamesString = settings?."${prefix}buttons"
  return buttonNamesString?.tokenize(' ')
}

void solicitDefaultButton(String prefix = null) {
  String header = 'Default Button'
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}dflt",
    title: "${h3(header)}",
    type: 'enum',
    multiple: false,
    options: [*getButtonNames(prefix), 'not_applicable'],
    defaultValue: defaultButton(prefix) ?: 'not_applicable',
    submitOnChange: isDynamic(),
    required: true
  )
}

String defaultButton(String prefix = null) {
  return settings?."${prefix}dflt"
}

void solicitNextAction(String pbsgName) {
  String header = 'Add a Test Action'
  if (pbsgName) { header << "for ${pbsgName}" }
  input(
    name: "${pbsgName}_nextAction",
    title: "${h3(header)}",
    type: 'enum',
    options: getTestActions(pbsgName),
    required: false,                 // Ensures user makes a selection!
    width: 3,
    submitOnChange: isDynamic()
  )
}

void removeNextAction(String pbsgName) {
  app.removeSetting("${pbsgName}_nextAction")
}

String getNextAction(String pbsgName) {
  return settings?."${pbsgName}_nextAction"
}

void solicitTestSequence(String testSequenceJson, String pbsgName) {
  String header = 'Save the Test Sequence'
  if (prefix) { header << "for ${pbsgName}" }
  input(
    name: "${pbsgName}_testSequence",
    title: "${h3(header)}",
    type: 'textarea',
    defaultValue: testSequenceJson,
    required: true,
    width: 9,
    submitOnChange: isDynamic()
  )
}

void removeTestSequence(String pbsgName = null) {
  app.removeSetting("${pbsgName}_testSequence")
}

ArrayList getTestSequence(String pbsgName) {
  return fromJson(settings?."${pbsgName}_testSequence")
}

void solicitPbsgConfig(String pbsgName) {
  // OVERVIEW
  //   - This method is called from within an App page section{...}
  //   - The client first populates the names of the PBSG buttons.
  //   - Once the list of buttons exists, the user selects a
  //     default (dflt) button or 'not_applicable'
  //   - All exposed fields can be edited until data entry is "Done".
  paragraph(h1("Provide '${b(pbsgName)}' Configuration Data"))
  ArrayList buttonList = getButtonNames("${pbsgName}_")
  Integer buttonCount = buttonList?.size()
  // Provide feedback to the client (dynamically).
  ArrayList feedback = []
  switch (buttonCount) {
    case 1:
      feedback << h1('Oops only one button was detected')
      feedback << "${b('Buttons')}: ${buttonList}, ${b('Button Count')}: ${buttonCount}"
      // Deliberately fall through to the next case!
    case null:
      feedback << h2("Create at least two buttons for PBSG ${b(pbsgName)}")
      feedback << i("Enter button names ${b('delimited with spaces ')}")
      break
    default:
      feedback << h2("Button names for PBSG ${b(pbsgName)}")
      feedback << i('button names are delimited with spaces')
  }
  paragraph(h2(feedback.join('<br/>')))
  solicitButtonNames("${pbsgName}_")
  if (buttonCount > 2) {
    solicitDefaultButton("${pbsgName}_")
  }
}

// --------
// FETCHERS
// --------

DevW getOrCreatePBSG(String pbsgName) {
  String dni = pbsgName   // Should NOT contain special chars except for '_'
  String name = dni.replaceAll('_', ' ') // Delim ' ' avoids special chars
  // Device Network Identifiers DO NOT include white space.
  // Device Names (exposed to Alexa ...) DO NOT include special characters.
  //-> logInfo(
  //->   'getOrCreatePBSG#A', ['',
  //->     "pbsgName: ${pbsgName}",
  //->     "dni: ${dni}",
  //->     "name: ${name}"
  //-> ].join('<br/>'))
  DevW d = getChildDevice(dni)
  if (d) {
    //-> logInfo('getOrCreatePBSG#B', "d: ${d}")
  } else {
    logWarn('getOrCreatePBSG', "Creating PBSG ${b(dni)}")
    d = addChildDevice(
      'wesmc',   // namespace
      'PBSG',    // typeName
      dni,       // Device Network Identifier (<pbsgName>_<button>)
      [
        isComponent: true,   // Lifecycle is tied to parent
        name: name,          // "PBSG <pbsgName>"
        label: name          // "PBSG <pbsgName>"
      ]
    )
  }
  return d
}

DevW getOrCreateVswWithToggle(String pbsgName, String button) {
  // Device Network Identifiers DO NOT include white space.
  // Device Names (exposed to Alexa ...) DO NOT include special characters.
  String dni = "${pbsgName}_${button}"
  String name = dni.replaceAll('_', ' ') // Delim ' ' avoids special chars
  DevW d = getChildDevice(dni)
  logInfo('getOrCreateVswWithToggle#A', ['',
    "pbsgName: ${pbsgName}",
    "button: ${button}",
    "dni: ${dni}",
    "name: ${name}",
    "d: ${d}"
  ].join('<br/>'))
  if (!d) {
    logWarn('getOrCreateVswWithToggle', "Creating VswWithToggle ${b(dni)}")
    d = addChildDevice(
      'wesmc',           // namespace
      'VswWithToggle',   // typeName
      dni,               // Device Network Identifier (<pbsgName>_<button>)
      [
        isComponent: true,   // Lifecycle is tied to parent
        name: name,          // "PBSG <pbsgName>"
        label: name          // "PBSG <pbsgName>"
      ]
    )
  }
  return d
}

// ------------------
// PBSG TO/FROM STATE
// ------------------

Map getPbsgState(String pbsgName) {
  logInfo('getPbsgState}', 'At entry')
  return atomicState."${pbsgName}"
}

void putPbsgState(Map pbsg) {
  logInfo('putPbsgState}', 'At entry')
  // Save the PBSG to atomicState
  atomicState."${pbsg.name}" = pbsg
  // Ensure child VSWs are on|off consistent with the PBSG state
  pbsg_ReconcileVswsToState(pbsg)
  // Invoke the client's callback function to consume the latest changes.
  // TBD: MIGRATE TO CALLBACK WITH MOVE TO DEVICE
  pbsg_ButtonOnCallback(pbsg)
}

// ---------------------------
// EXTRACT STATE FROM SETTINGS
// ---------------------------

Map gatherPbsgConfigFromSettings(String pbsgName) {
  // Collect data found in settings to produce a PBSG configuration Map.
  // For Apps collecting per-PBSG settings, supply a pbsgName.
  // When calling this method from within a PBSG device, use pbsgName = null.
  logInfo('gatherPbsgConfigFromSettings', "pbsgName: ${pbsgName}")
  String prefix = pbsgName ? "${pbsgName}_" : null
  return [
    name: pbsgName,
    instType: settings.instType ?: 'pbsg',
    buttons: getButtonNames(prefix),
    dflt: defaultButton(prefix)
  ]
}

// getOrCreatePBSG(pbsgName)

void pbsg_configure() {
  Map pbsgConfig = gatherPbsgConfigFromSettings()
  if (pbsgConfig.name) {
    // Process pbsg.all buttons and populate pbsg.lifo and pbsg.active
    // based on the current state of any discovered VSWs.
    pbsg.all.each { button ->
      if (button) {
        DevW vsw = getOrCreateVswWithToggle(pbsg.name, button)
  // AT THIS POINT, THE PBSG DEVICE EXISTS AND ITS initialize() HAS BEEN CALLED
  // ALBEIT WITH INSUFFICIENT DATA TO DO ANYTHING YET
  //   - Previously, initial data was manually pushed into the new device
  //     using the device drill down page. Then, "Save Preferences"
  //     triggered update(). Update() acknowledge the config BUT was not
  //     setup to build Child devies et al
  //   - Settings needs to be pushed into the App.
  //   - Then, settings need to be consumed to initiate operations
  //     inclusive of issueing state updates.
        pbsg.lifo.push(button)
        if (switchState(vsw) == 'on') {
          // Move the button from the LIFO to active
          //--debug-> logInfo('pbsg_configure#D', "Found VSW ${vsw.name} (${b('on')})")
          pbsg_ActivateButton(pbsg, button)
        } else {
          //--debug-> logInfo('pbsg_configure#E', "VSW ${vsw.name} (${i('off')})")
        }
        //--debug-> logInfo('pbsg_configure#F', pbsg_State(pbsg))
      } else {
        logError('pbsg_configure', "Encountered a null in pbsg.all (${pbsg.all})")
      }
    }
    if (!pbsg.active) { pbsg_EnforceDefault(pbsg) }
    putPbsgState(pbsg)  // Save to atomicState and issue client callback
  } else {
    logError('pbsg_configure', 'pbsgConfig.name is null')
  }
}

// ---------------------------
// PBSG STATE CHANGING METHODS
// ---------------------------

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

void pbsg_ReconcileVswsToState(Map pbsg) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state.
  // Get or Create the child VSW for each button.
  Map buttonToVsw = [:]
  pbsg?.all.each { button ->
    buttonToVsw << [ (button) : getOrCreateVswWithToggle(pbsg.name, button) ]
  }
  // Ensure VSW state matches button state.
  buttonToVsw.each { button, vsw ->
    if (pbsg.active == button) {
      if ( switchState(vsw) != 'on') { vsw.on() }
    } else {
      if ( switchState(vsw) != 'off') { vsw.off() }
    }
  }
}

// -----------------------------
// PBSG STATE VISIBILITY METHODS
// -----------------------------

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
    result += pbsg.lifo?.reverse().collect { button -> pbsg_ButtonState(pbsg, button) }.join(', ')
    result += ']'
  } else {
    logError('pbsg_State', 'Received null PBSG parameter.')
  }
  return result
}
