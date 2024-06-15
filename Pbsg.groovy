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
#include wesmc.lUtils
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput    // See wesmc.lUtils
import groovy.json.JsonSlurper   // See wesmc.lUtils

metadata {
  definition(
    name: 'PBSG',
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    importUrl: 'PENDING',
    singleThreaded: 'true'
  ) {
    // The attributes implied by capabilities are communicated/persisted
    // exclusively using sendEvent() - where the value is the attribute
    // that is being altered.
    capability 'Initialize'      // Commands: initialize()
    capability 'Configuration'   // Commands: configure()
    capability 'PushableButton'  // Attributes:
                                 //   - numberOfButtons: number
                                 //   - pushed: number
                                 // Commands: push(number)
    capability 'Refresh'         // Commands: refresh()
    command 'buttonNameToPushed', [[
      name: 'button',
      type: 'string',
      description: 'Returns the "keypad" position 1..N of the named button'
    ]]
    command 'activate', [[
      name: 'button',
      type: 'string',
      description: 'The button name to activate'
    ]]
    command 'deactivate', [[
      name: 'button',
      type: 'string',
      description: 'The button name to deactivate'
    ]]
    command 'activateLastActive', [[
    ]]
    // The folliwing attributes are comunicated/persisted exclusively using
    // sendEvent() - where the value is the attribute that is being altered.
    attribute 'jsonPbsg', 'String'
    attribute 'active', 'String'
    attribute 'jsonLifo', 'String'
    // Available for future use.
    //   - updateDataValue()
    //   - See https://docs2.hubitat.com/en/developer/device-object
    //   - Currently NOT used
  }
  preferences {
    input( name: 'buttons',
      title: "${b('Button Names')} (space delimited)",
      type: 'text',
      required: true
    )
    input( name: 'dflt',
      title: b('Default Button'),
      type: 'enum',
      multiple: false,
      options: (settings?.buttons?.tokenize(' ') ?: []) << 'not_applicable',
      defaultValue: 'not_applicable',
      required: true
    )
    input( name: 'instType',
      title: b('Type of PBSG'),
      type: 'text',
      defaultValue: 'jsonPbsg',
      required: true
    )
    input( name: 'logLevel',
      title: b('Logging Threshold ≥'),
      type: 'enum',
      multiple: false,
      options: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],
      defaultValue: 'TRACE',
      required: true
    )
  }
}

////
//// DEVICE LIFECYCLE METHODS
////

void installed() {
  // This method is called when a bare device is first constructed.
  // Settings ARE NOT likely to meet sufficiency requirements.
  logInfo('installed', 'Called, taking no action')
}

void uninstalled() {
  // This method is called on device tear down.
  logInfo('uninstalled', 'Called, taking no action')
}

void initialize() {
  // This method is called on hub startup (per capability "Initialize").
  ifSufficientSettingsConfigure('initialize()')
}

void refresh() {
  // This method is called (per capability "Refresh") when a refresh is
  // requested (manually or programmatically).
  ifSufficientSettingsConfigure('refresh()')
}

////
//// SETTINGS AND DERIVED STATE/ATTRIBUTES
////

// Per https://community.hubitat.com/t/device-updatesetting-not-updating-device-preferences/25201
// device.updateSetting("debugOutput",[value:"false",type:"bool"])
// void updateSetting(String name, Map options)
// void updateSetting(String name, Long value)
// void updateSetting(String name, Boolean value)
// void updateSetting(String name, String value)
// void updateSetting(String name, Double value)
// void updateSetting(String name, Date value)
// void updateSetting(String name, List value)
// device.updateSetting("paramOperationMode",[value:"2", type:"enum"])
//
// https://community.hubitat.com/t/app-updatesetting-how-to-update-a-setting-of-type-enum-or-color-stumped/103285

Boolean healthyButtonsPref(String buttons) {
  Boolean result = false
  ArrayList buttonsList = buttons.tokenize(' ')
  String markDirty = buttons
  markDirty?.replaceAll(/[\W_&&[^_ ]]/, '▮')
  if (buttons != markDirty) {
    logError('healthyButtonsPref', ["Invalid ${b('buttonsList')} preference:",
      buttons,
      "${markDirty} ('▮' denotes problematic character)."
    ].join('<br/>'))
  } else if (buttons == null || buttonsList.size() == 0) {
    logError('healthyButtonsPref', "No ${b('buttonsList')} were found.")
  } else if (buttonsList.size() < 2) {
    deficiency << "The button count, ${buttonsList} (${buttonsList.size()}) is < 2."
  } else {
    // Note: ArrayList → String
    buttons = buttonsList.join(' ')  // ArrayList → String
    device.updateSetting('buttons', [value: buttons, type: 'string'])
    atomicState.buttonsList = buttonsList
    logTrace('healthyButtonsPref', "settings.buttons: ${buttons}")
    result = true
  }
  return result
}

Boolean healthyDfltPref(String dflt) {
  result = false
  if (dflt) {
    ArrayList buttons = atomicState.buttonsList
    if (buttons) {
      if (buttons.contains(dflt)) {
        device.updateSetting('dflt', [value: dflt, type: 'string'])
        //-> logTrace('healthyDfltPref', "settings.dflt: ${settings.dflt}")
        result = true
      } else {
        logError('healthyDfltPref', "dflt (${dflt}) not among buttons (${buttons})")
      }
    } else {
      logError('healthyDfltPref', 'null atomicState.buttonsList')
    }
  } else {
    logError('healthyDfltPref', "dflt found null instead of 'not_applicable'")
  }
  return result
}

Boolean healthyInstTypePref(String instType) {
  result = false
  String markDirty = instType
  markDirty?.replaceAll(/[\W_&&[^_]]/, '▮')
  if (instType != markDirty) {
      logError('healthyInstTypePref', ["Invalid ${b('instType')} preference:",
      instType,
      "${markDirty} ('▮' denotes problematic character)."
    ].join('<br/>'))
  } else {
    device.updateSetting('instType', [value: instType, type: 'string'])
    //-> logTrace('healthyInstTypePref', "settings.instType: ${settings.instType}")
    result = true
  }
  return result
}

Boolean healthyLogLevelPref(String logLevel) {
  result = false
  ArrayList ok = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']
  if (!ok.contains(logLevel)) {
    logError('healthyLogLevelPref', "logLevel (${logLevel}) not in (${ok}).")
  } else {
    device.updateSetting('logLevel', [value: logLevel, type: 'enum'])
    //-> logTrace('healthyLogLevelPref', "settings.logLevel: ${logLevel}")
    result = true
  }
  return result
}

Boolean healthyPrefs(String callingFn) {
  Boolean result = false
  if (
    healthyButtonsPref(settings.buttons)
    && healthyDfltPref(settings.dflt)
    && healthyInstTypePref(settings.instType)
    && healthyLogLevelPref(settings.logLevel)
  ) {
    result = true
    logTrace('healthyPrefs', "Called by ${callingFn}, all OK")
  } else {
    logError('healthyPrefs', "Called by ${callingFn}, review logs")
  }
  return result
}

Integer buttonNameToPushed(String button) {
  // NOTE: Always computed on-the-fly
  Integer result = null
  ArrayList buttonsList = atomicState.buttonsList
  if (buttonsList) {
    result = buttonsList?.withIndex().collectEntries { b, i ->
      [(b), i+1]
    }."${button}"
  } else {
    logError('buttonNameToPushed', 'null atomicState.buttonsList')
  }
  return result
}

void updateNumberOfButtons() {
  logTrace(
    'updateNumberOfButtons',
    'Refreshing derived attribute "numberOfButtons"'
  )
  ArrayList buttonsList = atomicState.buttonsList
  if (buttonsList) {
    Integer curr_nOB = device.currentValue('numberOfButtons')
    Integer next_nOB = buttonsList.size()
    if (curr_nOB != next_nOB) {
      String desc = "${curr_nOB} → ${next_nOB}"
      logTrace('updateNumberOfButtons', desc)
      sendEvent(
        name: 'numberOfButtons',
        isStateChange: true,
        value: next_nOB,
        unit: '#',
        descriptionText: "numberOfButtons: ${desc}"
      )
    } else {
      logTrace('updateNumberOfButtons', 'No update required')
    }
  } else {
    logError('updateNumberOfButtons', 'null atomicState.buttonsList')
  }
}

void updated() {
  // PROCESS MANUAL SETTINGS CHANGE
  //   - This method is called when a human uses the Hubitat GUI's Device
  //     drilldown page to edit preferences (aka settings) AND presses
  //     'Save Preferences'.
  //   - Any issues are reported via the Hubitat logs.
  //   - The configure() method is callsed iff sufficient settings exist.
  ifSufficientSettingsConfigure('updated()')
}

void parse(String jsonConfig) {
  // PROCESS PROGRAMMATIC SETTINGS CHANGE
  //   - This method is exposed to clients and provides a programmatic
  //     mechanism for udpdating preferences (aka settings). Individual
  //     settings are updated iff per-field validation tests pass.
  //   - Any issues are reported via the Hubitat logs.
  //   - The configure() method is called iff sufficient settings exist.
  // Expected JSON
  //   A serialized JSON Map with four (OPTIONAL) keys:
  //     toJson([
  //        buttons: <String>,     // Adjusts settings.buttons
  //           dflt: <String>,     // Adjusts settings.dflt
  //       instType: <String>,     // Adjusts settings.instType
  //       logLevel: <enum>        // Adjusts settings.logLevel
  //     ])
  logTrace('parse', "Processing jsonConfig: ${jsonConfig}")
  Map parms = fromJson(jsonConfig)
  logTrace('parse', "Produced parms: ${parms}")
  if (
    healthyButtonsPref(parms.buttons ?: settings.buttons)
    && healthyDfltPref(parms.dflt ?: settings.dflt)
    && healthyInstTypePref(parms.instType ?: settings.instType)
    && healthyInstTypePref(parms.logLevel ?: settings.logLevel)
  ) {
    logTrace('parse', 'Good parms, Calling configure()')
    configure()
  }
}

String currentSettingsHtml() {
  return [
    b('SETTINGS:'),
    settings.collect { k, v -> "${b(k)}: ${v}" }.join(', ')
  ].join('<br/>')
}

////
//// CORE PBSG METHODS
////

Map getPBSG() {
  // Operations on a PBSG
  //   1. Use this method to "Checks Out" a PBSG Map which reflects the
  //      current state of attribute 'jsonPbsg' and derived attributes
  //     'active', 'lifo' and 'pushed'.
  //   2. Perform one or more operations - e.g., pbsg_ActivateButton(),
  //      pbsg_DeactivateButton(), pbsg_ActivateLastActive()
  //   3. "Check In" the altered PBSG Map with putPBSG() which publishes
  //      the required sendEvents().
  String curr_jsonPbsg = device.currentValue('jsonPbsg')
  Map pbsg = fromJson(curr_jsonPbsg)
  pbsg << [priorActive: pbsg.active]
  pbsg << [priorLifo: pbsg.lifo]
  logTrace('getPBSG', "Retrieved pbsg: ${pbsg}")
  return pbsg
}

void putPBSG(Map pbsg) {
  // Operations on a "Checked Out" PBSG - see getPBSG().
  //   - Publish carious sendEvent() updates based on the changes (if any)
  //     made to the PBSG.
  logTrace('putPBSG', "Processing pbsg: ${pbsg}")
  Boolean activeChanged = pbsg.active != pbsg.priorActive
  Boolean lifoChanged = pbsg.lifo != pbsg.priorLifo
  if (activeChanged || lifoChanged) {
    Map currPBSG = pbsg.findAll { k, v ->
      ['priorActive', 'priorLifo'].contains(k) == false
    }
    logTrace('putPBSG', "Updating attribute 'jsonPbsg': ${currPBSG}")
    String jsonPbsg = toJson(currPBSG)
    sendEvent(
      name: 'jsonPbsg',
      isStateChange: true,
      value: jsonPbsg,
      descriptionText: 'PLACEHOLDER ONLY'
    )
    logWarn('putPBSG', "jsonPbsg.descriptionText TBD ><")
  }
  if (activeChanged) {
    Integer priorPushed = buttonNameToPushed(pbsg.priorActive)
    Integer pushed = buttonNameToPushed(pbsg.active)
    String desc = "${pbsg.priorActive} (${priorPushed}) → ${pbsg.active} (${pushed})"
    logTrace('putPBSG', "Updating attribute 'active': ${pbsg.active}")
    sendEvent(
      name: 'active',
      isStateChange: true,
      value: pbsg.active,
      descriptionText: desc
    )
    logTrace('putPBSG', "Updating attribute 'pushed': ${pbsg.active}")
    sendEvent(
      name: 'pushed',
      isStateChange: true,
      value: pushed,
      descriptionText: desc
    )
  }
  if (lifoChanged) {
    logTrace('putPBSG', "Updating attribute 'lifo': ${pbsg.lifo}")
    sendEvent(
      name: 'jsonLifo',
      isStateChange: true,
      value: toJson(pbsg.lifo),
      descriptionText: "${pbsg.priorLifo} → ${pbsg.lifo}"
    )
  }
  logTrace('putPBSG', 'Refreshing child device state.')
  pbsg.lifo.each{ button -> turnOffVsw(button) }
  pbsg.active && turnOnVsw(pbsg.active)
}

void pbsg_ActivateButton(Map pbsg, String button) {
  logTrace('pbsg_ActivateButton', "target ${b(button)}")
  // Make 'button' active (if not already) in pbsg.
  if (pbsg?.active == button) {
    logWarn(
      'pbsg_ActivateButton',
      "${b(button)} was already activated"
    )
  } else if (!pbsg?.lifo.contains(button)) {
    logWarn(
      'pbsg_ActivateButton',
      "${b(button)} not found in lifo (${pbsg.lifo})"
    )
  } else {
    // Push any currently active button into lifo.
    if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
    // Remove target button from lifo and make it active.
    pbsg.lifo.removeAll([button])
    pbsg.active = button
  }
}

void pbsg_DeactivateButton(Map pbsg, String button) {
  logTrace('pbsg_DeactivateButton', "target ${b(button)}")
  if (pbsg.active != button) {
    logWarn('pbsg_DeactivateButton', "Button (${button}) IS NOT active. ")
  } else if (pbsg.active == settings.dflt) {
    logWarn(
      'pbsg_DeactivateButton',
      "Ignoring request to deactivate the dflt button ${settings.dflt}"
    )
  } else if (pbsg.active == button) {
    // Push the currently active button into lifo & clear active.
    pbsg.lifo.push(pbsg.active)
    pbsg.active = null
    // If a default button exists, move it from the Lifo to active.
    if (settings.dflt) {
      pbsg.lifo.removeAll([settings.dflt])
      pbsg.active = settings.dflt
    }
  }
}

void pbsg_ActivateLastActive(Map pbsg) {
  // NOTE: Groovy ArrayList Behavior (circa Q2'24)
  //   +----------------------+----------------------+
  //   |  Expected Behavior   |   Actual Behavior    |
  //   +----------------------+----------------------+
  //   | push(item) PREPENDS  |  push(item) APPENDS  |
  //   |  item to ArrayList.  |  item to ArrayList.  |
  //   +----------------------+----------------------+
  //   | pop() retrieves item | pop() retrieves item |
  //   |  from ArrayList at   |  from ArrayList at   |
  //   |          i=0         | i=(lifo.size() - 1)  |
  //   +----------------------+----------------------+
  Integer latestPushIndex = pbsg.lifo.size() - 1
  String button = pbsg.lifo[latestPushIndex]
  logTrace('pbsg_ActivateLastActive', "target ${b(button)}")
  pbsg_ActivateButton(pbsg, lastActive)
}

void pbsg_EnforceDefault(Map pbsg) {
  if (pbsg
      && !pbsg.active
      && settings.dflt
      && settings.dflt != 'not_applicable') {
    logTrace('pbsg_EnforceDefault', "Activating ${b(settings.dflt)}")
    result = pbsg_ActivateButton(pbsg, settings.dflt)
  }
}

String pbsg_ButtonState(Map pbsg, String button) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state
  if (button != null) {
    String tag = (button == pbsg.dflt) ? '*' : ''
    String summary = "${tag}<b>${button}</b> "
    DevW vsw = getChildDevice("${device.name}_${button}")
    String swState = vsw.switch
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
  // The supplied PBSG is used without making any changes to its state
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

String currentAttributesHtml() {
  return [b('CURRENT ATTRIBUTES:'),
    "${b('numberOfButtons:')} ${device.currentValue('numberOfButtons')}",
    "${b('pushed:')} ${device.currentValue('pushed')}",
    "${b('buttonsToPositions:')} ${device.currentValue('buttonsToPositions')}",
    "${b('pbsg:')} ${device.currentValue('jsonPbsg')}"
  ].join('<br/>')
}

////
//// STATE METHODS
////

String currentStateHtml() {
  return [b('CURRENT STATE:'),
  ].join('<br/>')
}

DevW getOrCreateVswWithToggle(String pbsgName, String button) {
  // Device Network Identifiers DO NOT include white space.
  // Device Names (exposed to Alexa ...) DO NOT include special characters.
  String dni = "${pbsgName}_${button}"
  String name = dni.replaceAll('_', ' ') // Delim ' ' avoids special chars
  DevW d = getChildDevice(dni)
  //-> logInfo('getOrCreateVswWithToggle#A', ['',
  //->   "pbsgName: ${pbsgName}",
  //->   "button: ${button}",
  //->   "dni: ${dni}",
  //->   "name: ${name}"
  //-> ].join(', '))
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
    //-> logInfo('getOrCreateVswWithToggle#A', ['',
    //->   "pbsgName: ${pbsgName}",
    //->   "button: ${button}",
    //->   "dni: ${dni}",
    //->   "name: ${name}",
    //->   "d: ${d}"
    //-> ].join('<br/>'))
  }
  return d
}

void configure() {
  // Start fresh wrt atomicState
  /*
  atomicState.remove('buttons')
  atomicState.remove('buttonsToVsws')
  atomicState.remove('buttonToVsws')
  atomicState.remove('curr_jsonPbsg')
  atomicState.remove('logLevel')
  atomicState.remove('weekdays')
  */
  logTrace('configure', 'Starting configuration')
  // Normally
  //   - This function is called by ifSufficientSettingsConfigure()
  //     iff all sufficiency tests pass.
  // Exception
  //   - It is possible to bypass the sufficiency tests and call configure
  //     directly.
  //   - configure() is accessible due to capability 'Configuration'
  updateNumberOfButtons()
  // Rebuid the PBSG "from scratch" - do not consult sendEvent() history.
  logTrace('configure', 'Creating PBSG "from scratch"')
  Map newPbsg = [
    active: null,          // String
    lifo: [],              // ArrayList
    priorActive: null,     // String
    priorLifo: []          // ArrayList
  ]
  ArrayList expectedChildDnis = []
  ArrayList buttonsList = atomicState.buttonsList
  buttonsList.each { button ->
    DevW vsw = getOrCreateVswWithToggle(device.getLabel(), button)
    expectedChildDnis << vsw.getDeviceNetworkId()
    newPbsg.lifo.push(button)
    if (vsw.switch == 'on') {
      // Move the button from the LIFO to active
      pbsg_ActivateButton(newPbsg, button)
    }
  }
  if (!newPbsg.active) { pbsg_EnforceDefault(newPbsg) }
  ArrayList currentChildDnis = getChildDevices().collect { d ->
    d.getDeviceNetworkId()
  }
  putPBSG(newPbsg)  // Generate PBSG sendEvent(s) (aka commit).
  ArrayList orphanedDevices = currentChildDnis?.minus(expectedChildDnis)
  logTrace('configure', 'Checking for orphaned child devices.')
  orphanedDevices.each { orphan ->
    logWarn('configure', "Removal of orphan device ${orphan} is TBD")
  }
}

void activate(String button) {
  // User accessible
  logInfo('activate', button)
  Map pbsg = getPBSG()
  pbsg_ActivateButton(pbsg, button)
  putPBSG(pbsg)
}

void deactivate(String button) {
  // User accessible
  logInfo('deactivate', button)
  pbsg = getPBSG()
  pbsg_DeactivateButton(pbsg, button)
  putPBSG(pbsg)
}

void activateLastActive() {
  logInfo('activateLastActive', 'no args')
  pbsg = getPBSG()
  pbsg_ActivateLastActive(pbsg)
  putPBSG(pbsg)
}

// Process Methods on behalf of Child Devices

String vswToButtonName(DevW d) {
  return d.getLabel().tokenize('_')[1]
}

void vswWithToggleOn(DevW d) {
  String button = vswToButtonName(d)
  activateButton(button)
}

void vswWithToggleOff(DevW d) {
  String button = vswToButtonName(d)
  deactivateButton(button)
}

void vswWithTogglePush(DevW d) {
  String button = vswToButtonName(d)
  (active == button)
    ? deactivateButton(button)
    : activateButton(button)
}

// Direct Child State Changes
//   - Using getChildDevice vs. maintaining an (overweight)
//     button-to-DevW Map (which is problematic in atomicState)

DevW getVsw(String button) {
  String dni = "${device.getLabel()}_${button}"
  DevW d = getChildDevice(dni)
  if (!d) {
    logError('getVsw', "No Device (${dni}) for button (${button}).")
  }
  return d
}

void turnOnVsw(String button) {
  logInfo('turnOnVsw', "button: ${button}")
  DevW d = getVsw(button)
  if (d) {
    // Parse expects a list (ArrayList) of commands (Maps)
    ArrayList commands = [] << [
      name: 'switch',
      value: 'on',
      descriptionText: "${dni} was turned on",
      isStateChange: true
    ]
    d.parse(commands)
  }
}

void turnOffVsw(String button) {
  logInfo('turnOffVsw', "button: ${button}")
  DevW d = getVsw(button)
  if (d) {
    // Parse expects a list (ArrayList) of commands (Maps)
    ArrayList commands = [] << [
      name: 'switch',
      value: 'off',
      descriptionText: "${dni} was turned off",
      isStateChange: true
    ]
    d.parse(commands)
  }
}

// Methods Expected for Advertised Capabilities

void push(Integer buttonNumber) {
  ArrayList buttonsList = atomicState?.buttons
  if (buttonsList) {
    String button = buttonsList[buttonNumber - 1]
    logInfo('push', "Received ${buttonNumber} (${button})")
    activateButton(button)
  } else {
    logError('push', 'null atomicState.buttonsList')
  }
}

//// UNUSED / UNSUPPORTED

void parse(ArrayList actions) {
  // This method is reserved for interaction with FUTURE parent devices.
  logWarn('parse', ['parse(ArrayList) ignored:',
    actions.join('<br/>')
  ].join('<br/>'))
}
