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

String toJson(def thing) {
  def output = new JsonOutput()
  return output.toJson(thing)
}

/* groovylint-disable-next-line MethodReturnTypeRequired */
def fromJson(String json) {
  def result
  if (json) {
    def slurper = new JsonSlurper()
    result = slurper.parseText(json)
  }
  return result
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

// +--------------------------------+--------------------------------|
// |          APP CONTEXT           |         DEVICE CONTEXT         |
// +--------------------------------+--------------------------------|
// | Data is collected via input()  |  Data is set via sendEvent()   |
// +--------------------------------+--------------------------------|
// |              Data is read using settings."${key}"               |
// +--------------------------------+--------------------------------|
// |  A prefix is used in settings  |                                |
// |     keys to allow data for     |   When collecting data for a   |
// |     multiple devices to be     |  single device, prefix = null  |
// |     collected concurrently     |                                |
// +--------------------------------+--------------------------------|

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

void solicitButtonNames(String prefix = null, Boolean dynamic = false) {
  String header = "${b('Button Names')} (space delimited)"
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}Buttons",
    title: "${h3(header)}",
    type: 'text',
    required: true,
    submitOnChange: dynamic
  )
}

ArrayList getButtonNames(String prefix = null) {
  // Tokenize the space-delimited buttons to produce the ArrayList
  String buttonNamesString = settings?."${prefix}Buttons"
  return buttonNamesString?.tokenize(' ')
}

void solicitDefaultButton(String prefix = null, Boolean dynamic = false) {
  String header = 'Default Button'
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}Dflt",
    title: "${h3(header)}",
    type: 'enum',
    multiple: false,
    options: [*getButtonNames(prefix), 'not_applicable'],
    defaultValue: defaultButton(prefix) ?: 'not_applicable',
    submitOnChange: dynamic,
    required: true
  )
}

String defaultButton(String prefix = null) {
  return settings?."${prefix}Dflt"
}

void solicitNextAction(String prefix = null) {
  String header = 'Add a Test Action'
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}NextAction",
    title: "${h3(header)}",
    type: 'enum',
    options: getTestActions(prefix),
    required: false,                 // Ensures user makes a selection!
    width: 3,
    submitOnChange: true
  )
}

void removeNextAction(String prefix = null) {
  app.removeSetting("${prefix}NextAction")
}

String getNextAction(String prefix = null) {
  return settings?."${prefix}NextAction"
}

void solicitTestSequence(String testSequenceJson, String prefix = null) {
  String header = 'Save the Test Sequence'
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}TestSequence",
    title: "${h3(header)}",
    type: 'textarea',
    defaultValue: testSequenceJson,
    required: true,
    width: 9,
    submitOnChange: true
  )
}

void removeTestSequence(String prefix = null) {
  app.removeSetting("${prefix}TestSequence")
}

ArrayList getTestSequence(String prefix = null) {
  return fromJson(settings?."${prefix}TestSequence")
}

void solicitPbsgConfig(String pbsgName) {
  // OVERVIEW
  //   - This method is called from within an App page section{...}
  //   - The client first populates the names of the PBSG buttons.
  //   - Once the list of buttons exists, the user selects a
  //     default (dflt) button or 'not_applicable'
  //   - All exposed fields can be edited until data entry is "Done".
  paragraph(h1("Provide '${b(pbsgName)}' Configuration Data"))
  ArrayList buttonList = getButtonNames(pbsgName)
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
  solicitButtonNames(pbsgName, true)       // true → dynamic
  if (buttonCount > 2) {
    solicitDefaultButton(pbsgName, true)   // true → dynamic
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
  logInfo(
    'getOrCreatePBSG#A', ['',
      "pbsgName: ${pbsgName}",
      "dni: ${dni}",
      "name: ${name}"
  ].join('<br/>'))
  DevW d = getChildDevice(dni)
  if (d) {
    logInfo('getOrCreatePBSG#B', "d: ${d}")
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

// ---------------------------
// EXTRACT STATE FROM SETTINGS
// ---------------------------

Map gatherPbsgConfigFromSettings(String pbsgName, String instType = 'pbsg') {
  // Collect data found in settings to produce a PBSG configuration Map.
  Map config = [:]
  if (pbsgName) {
    ArrayList buttonsFromSettings = getButtonNames(pbsgName)
    //--debug-> logInfo('gatherPbsgConfigFromSettings#A', "buttonsFromSettings: ${buttonsFromSettings}")
    String dfltFromSettings = defaultButton(pbsgName)
    //--debug-> logInfo('gatherPbsgConfigFromSettings#B', "dfltFromSettings: ${dfltFromSettings}")
    if (buttonsFromSettings) {
      // A minimal PBSG configuration Map exists
      config = [
        name: pbsgName,
        instType: instType,
        buttons: buttonsFromSettings,
        dflt: dfltFromSettings
      ]
      //--debug-> logInfo('gatherPbsgConfigFromSettings#C', "config: ${config}")
      // Do warn that no default button was specified
      if (!dfltFromSettings) {
        logWarn('pbsg_CollectSolicitedConfig', 'No default button was specified')
      }
    } else {
      // Insufficient data exists for a PBSG configuration Map
      logError('gatherPbsgConfigFromSettings', 'getButtonNames(pbsg) produced null')
    }
  }
  return config
}

Map pbsg_BuildToConfig(Map pbsgConfig) {
  logInfo('pbsg_BuildToConfig#A', "pbsgConfig: ${pbsgConfig}")
  // This method (re-)builds a PBSG and writes it to atomicState
  // Parameter Assumptions:
  //   The supplied Map (pbsg) IS NOT null and includes:
  //     - pbsg.name which IS NOT null
  //     - pbsg.all which IS NOT null and IS an ArrayList of button names
  DevW pbsg
  //--debug-> logInfo('pbsg_BuildToConfig#A', "pbsgConfig: ${pbsgConfig}")
  if (pbsgConfig.name) {
    pbsg = getOrCreatePBSG(pbsgConfig.name)
    logInfo('pbsg_BuildToConfig#B', "pbsg: ${pbsg}")
    pbsg.lifo = []
    //--debug-> logInfo('pbsg_BuildToConfig#C', pbsg_State(pbsg))
    // Process pbsg.all buttons and populate pbsg.lifo and pbsg.active
    // based on the current state of any discovered VSWs.
    pbsg.all.each { button ->
      if (button) {
        DevW vsw = getOrCreateVswWithToggle(pbsg.name, button)
        pbsg.lifo.push(button)
        if (switchState(vsw) == 'on') {
          // Move the button from the LIFO to active
          //--debug-> logInfo('pbsg_BuildToConfig#D', "Found VSW ${vsw.name} (${b('on')})")
          pbsg_ActivateButton(pbsg, button)
        } else {
          //--debug-> logInfo('pbsg_BuildToConfig#E', "VSW ${vsw.name} (${i('off')})")
        }
        //--debug-> logInfo('pbsg_BuildToConfig#F', pbsg_State(pbsg))
      } else {
        logError('pbsg_BuildToConfig', "Encountered a null in pbsg.all (${pbsg.all})")
      }
    }
    if (!pbsg.active) { pbsg_EnforceDefault(pbsg) }
    putPbsgState(pbsg)  // Save to atomicState and issue client callback
  } else {
    logError('pbsg_BuildToConfig', 'pbsgConfig.name is null')
  }
  return pbsg
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
