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
    attribute 'buttonsToPositions', 'map'
    attribute 'pbsg', 'map'
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
      defaultValue: 'pbsg',
      required: true
    )
    input( name: 'logLevel',
      title: b('Logging Threshold ≥'),
      type: 'enum',
      multiple: false,
      options: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],
      defaultValue: 'INFO',
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
    // Note: ArrayList -> String
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
      logError('healthyDfltPref', 'Cannot find atomicState.buttonsList')
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

Integer buttonNameToPushed(String s) {
  atomicState.buttonsList?.withIndex().collectEntries { b, i -> [(b), i+1] }."${s}"
}

void update_SettingsDerivatives() {
  logInfo('A.1.2.1.A', 'update_SettingsDerivatives')
  // ==> Update atomicState.buttonsList
  atomicState.buttonsList = settings.buttons?.tokenize(' ')
  logInfo('A.1.2.1.B', "update_SettingsDerivatives, aS.bL: ${atomicState.buttonsList}")
  // Update attributes that only depend on settings.
  // ==> Update numberOfButtons (attribute)
  Integer curr_numberOfButtons = device.currentValue('numberOfButtons')
  logInfo('A.1.2.1.C', "update_SettingsDerivatives, curr_nOB: ${curr_numberOfButtons}")
  ArrayList buttonsList = atomicState.buttonsList
  logInfo('A.1.2.1.D', "update_SettingsDerivatives, buttonsList: ${buttonsList}")
  Integer next_numberOfButtons = buttonsList?.size()
  logInfo('A.1.2.1.E', "update_SettingsDerivatives, number of buttons: ${curr_numberOfButtons} -> ${next_numberOfButtons}")
  if (curr_numberOfButtons != next_numberOfButtons) {
    logInfo('A.1.2.1.E.1', "update_SettingsDerivatives, Before sendEvent numberOfButtons")
    sendEvent(
      name: 'numberOfButtons',
      isStateChange: true,
      value: next_numberOfButtons,
      unit: '#',
      descriptionText: "numberOfButtons ${curr_numberOfButtons} → ${next_numberOfButtons}"
    )
    logInfo('A.1.2.1.E.2', "update_SettingsDerivatives, After sendEvent numberOfButtons")
  }
  // ==> Update buttonsToPositions (attribute)
  logInfo('A.1.2.1.F', "update_SettingsDerivatives, curr_b2P: Before buttonsToPositions Update")
  //Map curr_b2P = device.currentValue('buttonsToPositions')

  String yyy = 'wed'
  logInfo('#254', "${buttonsList?.withIndex().collectEntries { b, i -> [(b), i+1] }."${yyy}"}")
  logInfo('#255', "${buttonNameToPushed(yyy)}")
  Map curr_b2P = device.currentValue('buttonsToPositions')
  logInfo('A.1.2.1.G', "update_SettingsDerivatives, curr_b2P: ${curr_b2P}")
  logInfo('A.1.2.1.H', "update_SettingsDerivatives, b2P: ${curr_b2P} -> ${next_b2P}")
  if (curr_b2P != next_b2P) {
    logInfo('A.1.2.1.H.1', ['update_SettingsDerivatives',
      'Before sendEvent buttonsToPositions',
      "next_b2P: ${next_b2P}"
    ].join('<br/>'))
    sendEvent(
      name: 'buttonsToPositions',
      isStateChange: true,
      value: next_b2P,
      unit: '#',
      descriptionText: "buttonsToPositions: ${next_b2P}"
    )
    logInfo('A.1.2.1.H.2', ['update_SettingsDerivatives',
      "After sendEvent buttonsToPositions",
      "checking last: ${device.currentValue('buttonsToPositions')}"
    ].join('<br/>'))
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
  logInfo('parse', "JSON:<br/>${jsonConfig}")
  Map parms = fromJson(jsonConfig)
logInfo('A.1.1', "parse: ${parms}")
  if (
    healthyButtonsPref(parms.buttons ?: settings.buttons)
    && healthyDfltPref(parms.dflt ?: settings.dflt)
    && healthyInstTypePref(parms.instType ?: settings.instType)
    && healthyInstTypePref(parms.logLevel ?: settings.logLevel)
  ) {
logInfo('A.1.2', 'parse')
    configure()
logInfo('A.1.3', 'parse')
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

Map getPbsg(Map pbsg) {
  Map currPbsg = atomicState.currPbsg
  if (currPbsg) {
    logWarn(
      'getPbsg',
      "Already has atomicState.currPbsg: ${currPbsg}"
    )
  } else {
    // Copy the most-recently published PBSG state into
    // atomicState.currPbsg as a point of reference.
    // Note Map .asImmutable may be implied in State | atomicState.
    currPbsg = device.currentValue('pbsg')
    logInfo(
      'getPbsg',
      "Saving to atomicState currPbsg: ${currPbsg}"
    )
    atomicState.currPbsg = currPbsg
  }
  Map nextPbsg = currPbsg ?: [active: null, lifo: []]
  return nextPbsg
}

void putPbsg(Map nextPbsg) {
  Map currPbsg = atomicState.currPbsg
  logInfo('A.1.2.5.1', "putPbsg w/ Pbsg: ${currPbsg} -> ${nextPbsg}")
  //logInfo('putPbsg', "${currPbsg}, nextPbsg: ${nextPbsg}")
  // ==> Update pbsg (attribute)
  if (!nextPbsg.equals(currPbsg)) {
    logInfo('A.1.2.5.1.A', 'putPbsg, before PBSG sendEvent')
    sendEvent(
      name: 'pbsg',
      isStateChange: true,
      value: nextPbsg,
      descriptionText: 'PLACEHOLDER ONLY' //pbsg_State(nextPbsg)
    )
    logInfo('A.1.2.5.1.B', 'putPbsg, after PBSG sendEvent')
    //logInfo('putPbsg#B', "AFTER PBSG sendEvent >${nextPbsg}<")
  }
  //-> logInfo('putPbsg#C', 'placeholder')
  // ==> Update pushed (attribute)
  logInfo('A.1.2.5.2', "putPbsg, active: curr: ${currPbsg?.active} -> ${nextPbsg?.active}")
  if (!currPbsg || nextPbsg.active != currPbsg.active) {
    //-> logInfo('putPbsg#D', "PBSG: ${(atomicState.currPbsg as Map} -> ${nextPbsg}")
    // Note curr_b2P is expected to have been updated prior to
    // invocation of PBSG methods.
    logInfo('A.1.2.5.2.A', "putPbsg, before buttonsToPositions retrieval")
    Map curr_b2P = device.currentValue('buttonsToPositions')
    logInfo('A.1.2.5.2.B', "putPbsg, curr_b2P: ${curr_b2P}")
    Integer nextButton = null
    if (curr_b2P) {
      logInfo('A.1.2.5.2.B.1', "putPbsg, before nextButton active:${nextPbsg.active}")
      //logInfo('putPbsg#E', "curr_b2P: ${curr_b2P}")
      nextButton = curr_b2p?."${nextPbsg.active}"
      logInfo('A.1.2.5.2.B.2', "putPbsg, nextButton: ${nextButton}")
    } else {
      logInfo('A.1.2.5.2.B.3', 'No currentValue for buttonsToPositions')
    }
    logInfo('putPbsg#G', ['',
      "curr_b2P: ${curr_b2P}",
      "nextButton: ${nextButton}",
      "nextPbsg: ${nextPbsg}"
    ].join('<br/>'))
    logInfo('A.1.2.5.2.C', "putPbsg, w/ curr_b2P: ${curr_b2P}")
    sendEvent([
      name: 'pushed',
      isStateChange: true,
      value: nextButton,
      descriptionText: "Pushed ${nextButton} (${nextPbsg.active})"
    ])
    logInfo('A.1.2.5.2.D', "putPbsg, w/ curr_b2P: ${curr_b2P}")
  }
  logInfo('A.1.2.5.3', "nextPbsg: ${nextPbsg}")
  // Update child VSWs for consistency
  nextPbsg.lifo.each{ button ->
  logInfo('A.1.2.5.3.*', "nextPbsg: turnOffVsw(${button})")
    turnOffVsw(button)
  }
  logInfo('A.1.2.5.4', "nextPbsg.active: turnOnVsw(${nextPbsg.active})")
  nextPbsg.active && turnOnVsw(nextPbsg.active)
  logInfo('A.1.2.5.5', 'removing atomicState entry for currPbsg')
  atomicState.remove('currPbsg')  // Completed checkin cycle
  logInfo('A.1.2.5.2.E', "putPbsg, atomicState.currPbsg: ${atomicState.currPbsg}")
}

void pbsg_ActivateButton(Map nextPbsg, String button) {
  // Make 'button' active (if not already) in nextPbsg.
  if (nextPbsg?.active == button) {
    logWarn(
      'pbsg_ActivateButton',
      "${b(button)} was already activated"
    )
  } else if (!nextPbsg?.lifo.contains(button)) {
    logWarn(
      'pbsg_ActivateButton',
      "${b(button)} not found in lifo (${nextPbsg.lifo})"
    )
  } else {
    // Push any currently active button into lifo.
    if (nextPbsg.active) { nextPbsg.lifo.push(nextPbsg.active) }
    // Remove target button from lifo and make it active.
    nextPbsg.lifo.removeAll([button])
    nextPbsg.active = button
  }
  logInfo('pbsg_ActiveButton', "At exit, nextPbsg: ${nextPbsg}")
}

void pbsg_DeactivateButton(Map nextPbsg, String button) {
  if (nextPbsg.active != button) {
    logWarn('pbsg_DeactivateButton', "Button (${button}) IS NOT active. ")
  } else if (nextPbsg.active == settings.dflt) {
    logWarn(
      'pbsg_DeactivateButton',
      "Ignoring request to deactivate the dflt button ${settings.dflt}"
    )
  } else if (nextPbsg.active == button) {
    // Push the currently active button into lifo & clear active.
    nextPbsg.lifo.push(nextPbsg.active)
    nextPbsg.active = null
    // If a default button exists, move it from the Lifo to active.
    if (settings.dflt) {
      nextPbsg.lifo.removeAll([settings.dflt])
      nextPbsg.active = settings.dflt
    }
  }
}

void pbsg_ActivateLastActive(Map nextPbsg) {
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
  Integer latestPushIndex = nextPbsg.lifo.size() - 1
  pbsg_ActivateButton(nextPbsg, nextPbsg.lifo[latestPushIndex])
}

void pbsg_EnforceDefault(Map nextPbsg) {
  if (nextPbsg
      && !nextPbsg.active
      && settings.dflt
      && settings.dflt != 'not_applicable') {
    logInfo('pbsg_EnforceDefault', "Activating default ${b(settings.dflt)}")
    result = pbsg_ActivateButton(nextPbsg, settings.dflt)
  } //-> else {
    //-> logInfo('pbsg_EnforceDefault', ['DEBUG:',
    //->   "nextPbsg.active: ${nextPbsg.active}",
    //->   "settings.dflt: ${settings.dflt}"
    //-> ].join('<br/>'))
  //-> }
}

String pbsg_ButtonState(Map nextPbsg, String button) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state
  if (button != null) {
    String tag = (button == nextPbsg.dflt) ? '*' : ''
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

String pbsg_State(Map nextPbsg) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state
  //   IMPORTANT:
  //     LIFO push() and pop() are supported, *BUT* pushed items are
  //     appended (NOTE PREPENDED); so, the list needs to be reverse
  //     to look like a FIFO.
  String result
  if (nextPbsg) {
    result = "${b(nextPbsg.name)}: "
    result += (nextPbsg.active) ? "${pbsg_ButtonState(nextPbsg, nextPbsg.active)} " : ''
    result += '← ['
    result += nextPbsg.lifo?.reverse().collect { button -> pbsg_ButtonState(nextPbsg, button) }.join(', ')
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
    "${b('pbsg:')} ${device.currentValue('pbsg')}"
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
  atomicState.remove('buttons')
  atomicState.remove('buttonsToVsws')
  atomicState.remove('buttonToVsws')
  atomicState.remove('currPbsg')
  atomicState.remove('logLevel')
  atomicState.remove('weekdays')
logInfo('A.1.2.1', 'configure')
  // Normally
  //   - This function is called by ifSufficientSettingsConfigure()
  //     iff all sufficiency tests pass.
  // Exception
  //   - It is possible to bypass the sufficiency tests and call configure
  //     directly.
  //   - configure() is accessible due to capability 'Configuration'
  update_SettingsDerivatives()
logInfo('A.1.2.2', 'configure')
  // Create nextPbsg WITHOUT reliance on a prior sendEvent().
  Map nextPbsg = [
    active: null,          // String
    lifo: []               // ArrayList
  ]
  //=> Map buttonsToVsws = atomicState.buttonsToVsws ?: [:]
  ArrayList expectedChildDnis = []
  ArrayList buttonsList = atomicState.buttonsList
logInfo('A.1.2.3', 'configure')
  buttonsList.each { button ->
    //-> logInfo('configure#A', "button: ${button}")
    DevW vsw = getOrCreateVswWithToggle(device.getLabel(), button)
    //=> buttonsToVsws << [button: vsw]
    //=> logInfo('configure#B', "buttonsToVsws: ${buttonsToVsws}")
    expectedChildDnis << vsw.getDeviceNetworkId()
    //-> logInfo('configure#C', "expectedChildDnis: ${expectedChildDnis}")
    nextPbsg.lifo.push(button)
    //-> logInfo('configure#D', "lifo: ${nextPbsg.lifo}")
    //-> if (vsw.switch) == 'on') {
    if (vsw.switch == 'on') {
      // Move the button from the LIFO to active
      logInfo('configure', "activating ${button} w/ 'on' VSW")
      pbsg_ActivateButton(nextPbsg, button)
    }
  }
logInfo('A.1.2.4', 'configure')
  if (!nextPbsg.active) {
    //-> logInfo('configure#Fa', "dflt: ${settings.dflt}, nextPbsg: ${nextPbsg}")
    pbsg_EnforceDefault(nextPbsg)
    //-> logInfo('configure#Fb', "dflt: ${settings.dflt}, nextPbsg: ${nextPbsg}")
  }
  ArrayList currentChildDnis = getChildDevices().collect { d ->
    d.getDeviceNetworkId()
  }
  ArrayList orphanedDevies = currentChildDnis?.minus(expectedChildDnis)
  //-> logInfo('configure#G', ['',
  //->   "currentChildDnis: ${currentChildDnis}",
  //->   "expectedChildDnis: ${expectedChildDnis}",
  //->   "orphanedDevies: ${orphanedDevies}"
  //-> ])
  //-> logInfo('configure#H', "nextPbsg: ${nextPbsg}")
logInfo('A.1.2.5', 'configure')
  putPbsg(nextPbsg)  // Save to atomicState and generate PBSG events.
logInfo('A.1.2.6', 'configure')
  orphanedDevices.each { orphan ->
    logWarn('configure#J', "TBD REMOVE ${orphan} !!!")
  }
logInfo('A.1.2.7', 'configure')
}

void activate(String button) {
  // User accessible
  logInfo('activate', button)
  Map nextPbsg = getPbsg()
  pbsg_ActivateButton(nextPbsg, button)
  putPbsg(nextPbsg)
}

void deactivate(String button) {
  // User accessible
  logInfo('deactivate', button)
  nextPbsg = getPbsg()
  pbsg_DeactivateButton(nextPbsg, button)
  putPbsg(nextPbsg)
}

void activateLastActive() {
  logInfo('activateLastActive', 'no args')
  nextPbsg = getPbsg()
  pbsg_ActivateLastActive(nextPbsg)
  putPbsg(nextPbsg)
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

void turnOnVsw(String button) {
  logInfo('turnOnVsw', button)
  String dni = "${device.getLabel()}_${button}"
  DevW d = getChildDevice(dni)
  if (d) {
    d.parse([
      name: 'switch',
      value: 'on',
      descriptionText: "${dni} was turned on",
      isStateChange: true
    ])
  } else {
    logError(
      'turnOnVsw',
      "Device (${dni}) for button (${button}) not found"
    )
  }
}

void turnOffVsw(String button) {
  logInfo('turnOffVsw', button)
  Map b2v = atomicState.buttonToVsws
  DevW d = b2v?."${button}"
  if (d) {
    d.parse([
      name: 'switch',
      value: 'off',
      descriptionText: "${d.getLabel()} was turned off",
      isStateChange: true
    ])
  } else {
    logError('turnOffVsw', "Device for button (${button} not found),")
  }
}

// Methods Expected for Advertised Capabilities

void push(Integer buttonNumber) {
  String button = atomicState?.buttons[buttonNumber - 1]
  logInfo('push', "Received ${buttonNumber} (${button})")
  activateButton(button)
}

//// UNUSED / UNSUPPORTED

void parse(ArrayList actions) {
  // This method is reserved for interaction with FUTURE parent devices.
  logWarn('parse', ['parse(ArrayList) ignored:',
    actions.join('<br/>')
  ].join('<br/>'))
}

