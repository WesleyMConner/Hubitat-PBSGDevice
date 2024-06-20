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
// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lUtils  // Requires the following imports.
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput as JsonOutput
import groovy.json.JsonSlurper as JsonSlurper
import java.lang.Math as Math
import java.lang.Object as Object

metadata {
  definition(
    name: 'PBSG',
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    importUrl: 'PENDING',
    singleThreaded: 'true'
  ) {
    //// CAPABILITIES
    ////   - Convey common "Commands" and/or "Attributes"
    capability 'Initialize'      // Commands: initialize()
    capability 'Configuration'   // Commands: configure()
    capability 'PushableButton'  // Attributes:
                                 //   - numberOfButtons: number
                                 //   - pushed: number
                                 // Commands: push(number)
    capability 'Refresh'         // Commands: refresh()
    //// COMMANDS
    ////   - The subset of device functions available to clients
    command 'buttonNameToPushed', [[
      name: 'button', type: 'String',
      description: 'returns button position 1..N'
    ]]
    command 'activate', [
      [name: 'button', type: 'String', description: 'button to activate']
    ]
    command 'deactivate', [
      [name: 'button', type: 'String', description: 'button to deactivate']
    ]
    command 'activateLastActive', []
    //// COMMANDS FOR TESTING
    ////   - Client Apps (e.g., Demo-PBSG.groovy) can access child devices.
    ////     (e.g., instances of wesmc.PBSG)
    ////   - Client Apps cannot access their grandchild devices
    ////     (e.g., instances of wesmc.VswWithToggle devices).
    ////   - Apps can use input() to request access to arbitrary devices.
    ////       input 'TBD', title: ..., multiple: true,...
    ////   - The following commands give wesmc.PBSG Client Apps an east way
    ////     to simulate (external) VswWithToggle on(), off() and toggle().
    command 'testVswOn', [
      [name:'button', type:'String', description:'TEST vsw.on()']
    ]
    command 'testVswOff', [
      [name:'button', type:'String', description:'TEST vsw.off()']
    ]
    command 'testVswPush', [
      [name:'button', type:'String', description:'TEST vsw.push()']
    ]
    //// ATTRIBUTES
    ////   - Are comunicated AND persisted using device.sendEvent()
    ////   - Current values are read using device.currentValue(...)
    attribute 'jsonPbsg', 'String'
    attribute 'active', 'String'
    //----> attribute 'jsonLifo', 'String'
  }
  //// PREFERENCES
  ////   - MANUAL adjustents are made via the Hubitat GUI Device drilldown page.
  ////   - PROGRAMMATIC adjustments are made via parse(String json) below.
  preferences {
    input( name: 'buttons',
      title: "${b('Button Names')} (space delimited)",
      type: 'Text',
      required: true
    )
    // IMPORTANT (as of Q2'24)
    //   - Ideally, the Preference 'dflt' would be of type 'Enum' where
    //     the values in the Enum are assembled dynamically.
    //   - Per Mike Maxwell, "Enum inputs in drivers ARE NOT DYNAMIC" !!!
    //     https://community.hubitat.com/t/driver-commands-with-enum/2110/5
    //   - Enum preferences CANNOT BE UPDATED via device.updateSetting().
    //   - Enums must be fixed (see logLevel below).
    //   - Neil Anderson suggests using a custom command instead.
    //     https://community.hubitat.com/t/driver-commands-with-enum/2110/11
    //   - Below, Preference 'dflt' is of type 'String' so that it operates
    //     symmetrically alongside other Preferences - e.g., see parms().
    input( name: 'dflt',
      title: [
        b('Default Button'),
        i('(must be present in "buttons" or "not_applicable")')
      ].join('<br/>'),
      type: 'String',
      multiple: false,
      defaultValue: 'not_applicable',
      required: false
    )
    input( name: 'instType',
      title: b('Type of PBSG'),
      type: 'Text',
      defaultValue: 'jsonPbsg',
      required: true
    )
    input( name: 'logLevel',
      title: b('Logging Threshold ≥'),
      type: 'Enum',         // Here, the Enum is fixed (not dynamic), so "okay".
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
  // Called when a bare device is first constructed.
  // Note: Settings ARE NOT likely to meet sufficiency requirements.
  logTrace('installed', 'Called, taking no action')
}

void uninstalled() {
  // Called on device tear down.
  logTrace('uninstalled', 'Called, taking no action')
}

void initialize() {
  // Called on hub startup (per capability "Initialize").
  logTrace('initialize', 'Checking sufficiency of current settings')
  ifSufficientSettingsConfigure('initialize()')
}

void refresh() {
  // Called when a refresh is requested (per capability "Refresh").
  ifSufficientSettingsConfigure('refresh()')
}

void updated() {
  // Called when a human uses the Hubitat GUI's Device drilldown page to edit
  // preferences (aka settings) AND presses 'Save Preferences'.
  ifSufficientSettingsConfigure('updated()')
}

void configure() {
  // Callable on demand per capability 'Configuration'.
  if (
    healthyButtonsPref(settings.buttons)
    && healthyDfltPref(settings.dflt)
    && healthyInstTypePref(settings.instType)
    && healthyInstTypePref(settings.logLevel)
  ) {
    logTrace(
      'configure',
      'Preferences are healthy, rebuilding PBSG (from scratch).'
    )
    rebuildPbsg()
  } else {
    logError('configure', 'Unhealthy Preferences/Settings, see Hubitat logs.')
  }
}

//// SETTINGS VALIDATION
////   - Check MANUAL settings adjustments for updated()
////   - Check PROGRAMMATIC adjustments for parse(String json)

void updateButtonsTrio(ArrayList buttonsList) {
  // This updates (and ensures the conistency of):
  //   - atomicState.buttonsList      // ArrayList
  //   - settings.buttons             // String
  //   - attribute 'numberOfButtons'  // Number
  atomicState.buttonsList = buttonsList
  device.updateSetting('buttons', [value: buttonsList.join(' '), type: 'String'])
  if (buttonsList) {
    Integer curr_nOB = device.currentValue('numberOfButtons')
    Integer next_nOB = buttonsList.size()
    if (curr_nOB != next_nOB) {
      String desc = "${i(curr_nOB)} → ${b(next_nOB)}"
      logTrace(
        'updateButtonsTrio',
        "Updating attribute ${b('numberOfButtons')}: ${desc}"
      )
      device.sendEvent(
        name: 'numberOfButtons',
        isStateChange: true,
        value: next_nOB,
        unit: '#',
        descriptionText: desc
      )
    }
  }
}

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
    logTrace(
      'healthyButtonsPref',
      'Updating button-related atomicState, settings and attributes.')
    updateButtonsTrio(buttonsList)
    result = true
  }
  return result
}

Boolean healthyDfltPref(String dflt) {
  result = false
  if (dflt == 'not_applicable') {
    result = true
  } else if (dflt) {
    ArrayList buttonsList = atomicState.buttonsList
    if (buttonsList) {
      if (buttonsList.contains(dflt)) {
        device.updateSetting('dflt', [value: dflt, type: 'String'])
        result = true
      } else {
        logError(
          'healthyDfltPref',
          "dflt (${b(dflt)}) not among buttons (${bList(buttonsList)})"
        )
      }
    }
  } else {
    // Parse expects dflt == 'not_applicable' (vs null) for clarity."
    logError('healthyDfltPref', "found null dflt, expected 'not_applicable'")
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
    device.updateSetting('instType', [value: instType, type: 'String'])
    result = true
  }
  return result
}

Boolean healthyLogLevelPref(String logLevel) {
  result = false
  ArrayList options = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']
  if (!options.contains(logLevel)) {
    logError(
      'healthyLogLevelPref',
      "logLevel (${b(logLevel)}) not in ${bList(options)}."
    )
  } else {
    device.updateSetting('logLevel', [value: logLevel, type: 'Enum'])
    result = true
    // Cascase logLevel to VswWithToggle child devices
    atomicState.buttonsList.each{ button ->
      adjustVswLogLevel(button, logLevel)
    }
  }
  return result
}

Integer buttonNameToPushed(String button) {
  // Button name to button 'keypad' position is always computed 'on-the-fly'.
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
  //           dflt: <String>,     // Leverages setDefaultButton()
  //       instType: <String>,     // Adjusts settings.instType
  //       logLevel: <Enum>        // Adjusts settings.logLevel
  //     ])
  Map parms = fromJson(jsonConfig)
  logTrace('parse', "Received parms: ${bMap(parms)} (from Json)")
  if (
    healthyButtonsPref(parms.buttons ?: settings.buttons)
    && healthyDfltPref(parms.dflt ?: settings.dflt)
    && healthyInstTypePref(parms.instType ?: settings.instType)
    && healthyLogLevelPref(parms.logLevel ?: settings.logLevel)
  ) {
    logTrace('parse', "Parms are healthy. Re-Building PBSG (from scratch).")
    rebuildPbsg()
  }
}

String currentSettingsHtml() {
  return [
    b('SETTINGS:'),
    settings.collect { k, v -> "${i(k)}: ${b(v)}" }.join(', ')
  ].join('<br/>')
}

void rebuildPbsg() {
  Map pbsg = newEmptyPbsg()
  ArrayList expectedChildDnis = []
  ArrayList buttonsList = atomicState.buttonsList
  buttonsList.each { button ->
    ChildDevW vsw = getOrCreateVswWithToggle(device.getLabel(), button)
    expectedChildDnis << vsw.getDeviceNetworkId()
    pbsg.lifo.push(button)
    if (vsw.switch == 'on') {
      // Move the button from the LIFO to active
      pbsg_ActivateButton(pbsg, button)
    }
    logTrace('rebuildPbsg', pbsg_State(pbsg))
  }
  if (!pbsg.active && settings.dflt && settings.dflt != 'not_applicable') {
    pbsg_EnforceDefault(pbsg)
  }
  ArrayList currentChildDnis = getChildDevices().collect { d ->
    d.getDeviceNetworkId()
  }
  logInfo('rebuildPbsg', 'Synchronizing child devices and sending events.')
  ciPbsg(pbsg)  // Generate PBSG sendEvent(s) (aka commit).
  ArrayList orphanedDevices = currentChildDnis?.minus(expectedChildDnis)
  orphanedDevices.each { dni ->
    logWarn('rebuildPbsg', "Removing orphaned device ${b(dni)}.")
    deleteChildDevice(dni)
  }
}

//// DEVELOP THE NEXT PBSG STATE UPDATE
////   - Changes are made on an in-memory PBSG Map
////       - The Map IS NOT persisted in state | atomicState
////       - The Map has prior (last-published) and prospective fields
////   - Child devices DO NOT reflect prospective fields
////   - Child devices are synchronized just before issuing a sendEvent()

Map newEmptyPbsg() {
  Map pbsg = [
    active: null,          // String
    lifo: [],              // ArrayList
    priorActive: null,     // By definition, "new" suggests no history.
    priorLifo: []          // By definition, "new" suggests no history.
  ]
  logTrace('newEmptyPbsg', bMap(pbsg))
  return pbsg
}

Map coPbsg() {                                             // aka checkOutPbsg()
  // "Check Out" a PBSG Map with the last-published PBSG attriute value.
  String curr_jsonPbsg = device.currentValue('jsonPbsg')
  Map pbsg = fromJson(curr_jsonPbsg)
  pbsg << [priorActive: pbsg.active, priorLifo: pbsg.lifo]
  logTrace('coPbsg', "returning: ${bMap(pbsg)}")
  return pbsg
}

Map pbsg_CoreKeysOnly(Map pbsg) {
  ArrayList coreKeys = ['active', 'lifo']
  Map corePbsg = pbsg.inject([:]) { m, k, v ->
    if (coreKeys.contains(k)) { m."${k}" = v }
    return m
  }
  logTrace('pbsg_CoreKeysOnly', "returning: ${bMap(corePbsg)}")
  return corePbsg
}

void ciPbsg(Map pbsg) {                                     // aka checkInPbsg()
  // Operations on a "Checked Out" PBSG - see coPbsg().
  //   - Publish carious sendEvent() updates based on the changes (if any)
  //     made to the PBSG.
  Boolean activeChanged = pbsg.active != pbsg.priorActive
  Boolean lifoChanged = pbsg.lifo != pbsg.priorLifo
  if (activeChanged || lifoChanged) {
    pbsg.lifo.each{ button -> turnOffVsw(button) }
    pbsg.active && (pbsg.active != 'not_applicable') && turnOnVsw(pbsg.active)
    String jsonPbsg = toJson(pbsg_CoreKeysOnly(pbsg))
    logTrace('ciPbsg', "Updating attribute ${b('jsonPbsg')}: ${jsonPbsg}")
    device.sendEvent(
      name: 'jsonPbsg',
      isStateChange: true,
      value: jsonPbsg,
      descriptionText: pbsg_State(pbsg)
    )
  }
  if (activeChanged) {
    String activeDesc = "${i(pbsg.priorActive)} → ${b(pbsg.active)}"
    logTrace('ciPbsg', "Updating attribute ${b('active')}: ${activeDesc}")
    device.sendEvent(
      name: 'active',
      isStateChange: true,
      value: pbsg.active,
      descriptionText: activeDesc
    )
    Integer priorPushed = buttonNameToPushed(pbsg.priorActive)
    Integer pushed = buttonNameToPushed(pbsg.active)
    String pushedDesc = "${i(priorPushed)} → ${b(pushed)}"
    logTrace('ciPbsg', "Updating attribute ${b('pushed')}: ${pushedDesc}")
    device.sendEvent(
      name: 'pushed',
      isStateChange: true,
      value: pushed,
      descriptionText: pushedDesc
    )
  }
//---->   if (lifoChanged) {
//---->     String desc = "${i(pbsg.priorLifo)} → ${b(pbsg.lifo)}"
//---->     logTrace('ciPbsg', "Updating attribute ${b('lifo')}: ${desc}")
//---->     device.sendEvent(
//---->       name: 'jsonLifo',
//---->       isStateChange: true,
//---->       value: toJson(pbsg.lifo),
//---->       descriptionText: desc
//---->     )
//---->   }
}

Map pbsg_ActivateButton(Map pbsg, String button) {
  // Make 'button' active (if not already) in pbsg.
  if (pbsg?.active == button) {
    logTrace(
      'pbsg_ActivateButton',
      "${b(button)} was already activated"
    )
  } else if (!pbsg?.lifo.contains(button)) {
    logWarn(
      'pbsg_ActivateButton',
      "${b(button)} not found in lifo (${bList(pbsg.lifo)})"
    )
  } else {
    // Push any currently active button into lifo.
    if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
    // Remove target button from lifo and make it active.
    pbsg.lifo.removeAll([button])
    pbsg.active = button
    logTrace('pbsg_ActivateButton', "${b(button)} ⟹ ${pbsg_State(pbsg)}")
  }
  return pbsg
}

Map pbsg_DeactivateButton(Map pbsg, String button) {
  logTrace('pbsg_DeactivateButton', "Deactivating ${b(button)}")
  if (pbsg.active != button) {
    logInfo('pbsg_DeactivateButton', "Button (${button}) IS NOT active. ")
  } else if (pbsg.active == settings.dflt) {
    logInfo(
      'pbsg_DeactivateButton',
      "Ignoring request to deactivate the dflt button ${b(settings.dflt)}"
    )
  } else if (pbsg.active == button) {
    // Push the currently active button into lifo & clear active.
    pbsg.lifo.push(pbsg.active)
    pbsg.active = null
    // If a default button exists, move it from the Lifo to active.
    if (settings.dflt && (settings.dflt != 'not_applicable')) {
      pbsg.lifo.removeAll([settings.dflt])
      pbsg.active = settings.dflt
    }
    logTrace('pbsg_DeactivateButton', "${b(button)} ⟹ ${pbsg_State(pbsg)}")
  }
  return pbsg
}

Map pbsg_ActivateLastActive(Map pbsg) {
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
  logTrace('pbsg_ActivateLastActive', "Activating ${b(button)}")
  pbsg_ActivateButton(pbsg, button)
  return pbsg
}

Map pbsg_EnforceDefault(Map pbsg) {
  if (pbsg?.active == null && settings?.dflt && settings.dflt != 'not_applicable') {
    logTrace('pbsg_EnforceDefault', "Activating ${b(settings.dflt)}")
    result = pbsg_ActivateButton(pbsg, settings.dflt)
  }
  return pbsg
}

String buttonState(String button) {
  result = ''
  if (button != null) {
    String tag = (button == settings.dflt) ? '*' : ''
    result += "${tag}${b(button)} "
    ChildDevW d = getChildDevice("${device.getLabel()}_${button}")
    if (d) {
      switch(d.currentValue('switch')) {
        case 'on':
          result += '(<b>on</b>)'
          break
        case 'off':
          result += '(<em>off</em>)'
          break
        default:
          result += '(--)'
      }
    } else {
      result += "(tbd)"
    }
  } else {
    logError('buttonState', 'button arg is NULL')
  }
  return result
}

String pbsg_State(Map pbsg) {
  // IMPORTANT:
  //   LIFO push() and pop() are supported, *BUT* pushed items are appended
  //   (not prepended). See "reverse()" below, which compensates.
  String result
  if (pbsg) {
    ArrayList table = []
    table << '<span style="display:inline-table;">'
    table << '<table><tr>'
    table << "<td>${devHued(device)}:&nbsp;</td>"
    table << tdBordered( pbsg.active ? buttonState(pbsg.active) : 'null' )
    table << '<td>&nbsp;←&nbsp;</td>'
    pbsg.lifo?.reverse().each { button ->
      table << tdBordered( buttonState(button) )
    }
    table << '</tr></table></span>'
    result = table.join()
  } else {
    result = "${devHued(device)}: null"
  }
  return result
}

//// CLIENT-EXPOSED COMMANDS
////   - A PBSG map is checked out (co)
////   - The operation is performed
////   - The PBSG map is checked in (ci)

void activate(String button) {
  logTrace('activate', button)
  Map pbsg = coPbsg()
  pbsg_ActivateButton(pbsg, button)
  ciPbsg(pbsg)
}

void deactivate(String button) {
  logTrace('deactivate', button)
  pbsg = coPbsg()
  pbsg_DeactivateButton(pbsg, button)
  ciPbsg(pbsg)
}

void activateLastActive() {
  logTrace('activateLastActive', i('This command requires no parameters.'))
  pbsg = coPbsg()
  pbsg_ActivateLastActive(pbsg)
  ciPbsg(pbsg)
}

void testVswOn(String button) {
  logTrace('testVswOn', 'Simulating an external vswWithToggle.on()')
  getVsw(button).on()
}

void testVswOff(String button) {
  logTrace('testVswOn', 'Simulating an external vswWithToggle.off()')
  getVsw(button).off()
}

void testVswPush(String button) {
  logTrace('testVswOn', 'Simulating an external vswWithToggle.push()')
  getVsw(button).push()
}

////
//// STATE METHODS
////

ChildDevW getOrCreateVswWithToggle(String pbsgName, String button) {
  // Device Network Identifier (DNI) does not include white space.
  // Device Names / Labels limit special characters to '_'.
  String dni = "${pbsgName}_${button}"
  String name = dni.replaceAll('_', ' ')
  ChildDevW d = getChildDevice(dni)
  if (!d) {
    logWarn(
      'getOrCreateVswWithToggle',
      "Creating VswWithToggle instance: ${b(dni)}"
    )
    d = addChildDevice(
      'wesmc',               // namespace
      'VswWithToggle',       // typeName
      dni,                   // Device Network Identifier (<pbsgName>_<button>)
      [
        isComponent: true,   // Lifecycle is tied to parent
        name: name,          // "PBSG <pbsgName>"
        label: name          // "PBSG <pbsgName>"
      ]
    )
  }
  return d
}

//// PROCESS METHODS ON BEHALF OF CHILD DEVICES
////   - A wmc.VswWithTogge is of class com.hubitat.app.DeviceWrapper (DevW)
////     when it calls parent.*() - essentially forwarding the request to
////     one of the following four methods.

String vswToButtonName(DevW d) {
  return d.getDeviceNetworkId().tokenize('_')[1]
}

void vswWithToggleOn(DevW d) {
  String button = vswToButtonName(d)
  activate(button)
}

void vswWithToggleOff(DevW d) {
  String button = vswToButtonName(d)
  deactivate(button)
}

void vswWithTogglePush(DevW d) {
  String button = vswToButtonName(d)
  (active == button)
    ? deactivate(button)
    : activate(button)
}

// Direct Child State Changes
//   - Using getChildDevice vs. maintaining an (overweight)
//     button-to-ChildDevW Map (which is problematic in atomicState)

ChildDevW getVsw(String button) {
  String dni = "${device.getLabel()}_${button}"
  ChildDevW d = getChildDevice(dni)
  if (!d) {
    logError('getVsw', "No Device (${devHued(d)}) for button (${b(button)}).")
  }
  return d
}

void turnOnVsw(String button) {
  ChildDevW d = getVsw(button)
  logTrace('turnOnVsw', d.getDeviceNetworkId())
  if (d) {
    // Parse expects a list (ArrayList) of commands (Maps)
    ArrayList commands = [] << [
      name: 'switch',
      value: 'on',
      descriptionText: "Turned ${b('on')} ${devHued(d)}",
      isStateChange: true
    ]
    d.parse(commands)
  }
}

void turnOffVsw(String button) {
  if (button) {
    ChildDevW d = getVsw(button)
    logTrace('turnOffVsw', devHued(d))
    if (d) {
      // Parse expects a list (ArrayList) of commands (Maps)
      ArrayList commands = [] << [
        name: 'switch',
        value: 'off',
        descriptionText: "Turned ${b('off')} ${devHued(d)}",
        isStateChange: true
      ]
      d.parse(commands)
    }
  } else {
    logError('turnOffVsw', 'Received null parameter "button"')
  }
}

void adjustVswLogLevel(String button, String logLevel) {
  ChildDevW d = getVsw(button)
  logTrace(
    'adjustVswLogLevel',
    "${devHued(d)} setting logLevel to ${logLevel}"
  )
  if (d) {
    // Parse expects a list (ArrayList) of commands (Maps)
    ArrayList commands = [] << [ name: 'logLevel', value: logLevel ]
    d.parse(commands)
  }
}

void push(Integer buttonNumber) {
  ArrayList buttonsList = atomicState?.buttons
  if (buttonsList) {
    String button = buttonsList[buttonNumber - 1]
    logInfo('push', "Received ${b(buttonNumber)} (${button})")
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
