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
#include WesMC.lUtils  // Requires the following imports.
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
    namespace: 'WesMC',
    author: 'Wesley M. Conner',
    description: """A virtual PushButtonSwitchGroup (PBSG) device manages two
or more WesMC.VswWithToggle child devices which are mutually-exclusive 'on'.
Optionally, one child device can be designated the 'default' device - i.e.,
turned "on" if no other device is turned 'on'.""",
    category: '',   // As of Q2'24 Not used
    iconUrl: '',    // As of Q2'24 Not used
    iconX2Url: '',  // As of Q2'24 Not used
    documentationLink: 'A Hubitat Community post is pending',
    importUrl: 'https://github.com/WesleyMConner/Hubitat-PBSGLibrary',
    singleThreaded: 'false',
  ) {
    //// CAPABILITIES
    ////   - Supported common "Commands" and "Attributes"
    ////       - Commands are the subset of device methods exposed to clients.
    ////       - Attributes are persisted using device.sendEvent() and read
    ////         using device.currentValue(...)
    capability 'Initialize'      // Commands: initialize()
    capability 'Configuration'   // Commands: configure()
    capability 'PushableButton'  // Attributes:
                                 //   - numberOfButtons: number
                                 //   - pushed: number
                                 // Commands: push(number)
    capability 'Refresh'         // Commands: refresh()
    //// COMMANDS
    ////   - Additional "Commands" (beyond Capabilities) exposed to clients
    ////   - Commands with the prefix "Test":
    ////       - The following commands give WesMC.PBSG Client Apps an easy way
    ////         to simulate external actions in VswWithToggle child devices.
    ////       - Without these commands:
    ////           - Parent Apps (e.g., Demo-PBSG.groovy) can only access child
    ////             devices (e.g., instances of WesMC.PBSG). Parent Apps cannot
    ////             access grandchild devices (e.g., VswWithToggle devices).
    ////           - Parent Apps would have to use the input() Block Method for\
    ////             permission to access individual VswWithToggle devices.
    ////             See https://docs2.hubitat.com/en/developer/app/preferences.
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
    ////   - Additional "Attributes" (beyond Capabilities) exposed to clients
    attribute 'jsonPbsg', 'String'
    attribute 'active', 'String'
  }
  preferences {
    input( name: 'buttons',
      title: "${b('Button Names')} (space delimited)",
      type: 'Text',
      required: true
    )
    input( name: 'dflt',
      title: [
        b('Default Button'),
        i('(must be present in "buttons" or "not_applicable")')
      ].join('<br/>'),
      type: 'String',
      multiple: false,     // 'buttons' are dynamic! So, Enum is not an option
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
      title: b('PBSG Log Threshold ≥'),
      type: 'Enum',        // This is a fixed (not dynamic) Enum; so, okay
      multiple: false,
      options: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],
      defaultValue: 'TRACE',
      required: true
    )
    input( name: 'logVswActivity',
      title: b('Enable/Disable VSW Logging'),
      type: 'Boolean',
      defaultValue: false,
      required: true
    )
  }
}

void addCommandToQueue(Map command) {
  ArrayList q = atomicState.requestQueue
  logTrace('addCommandToQueue', "#132 q: ${bList(q)}, command: ${bMap(command)}")
  if (q == null) { q = [] }
  logTrace('addCommandToQueue', "#134 q: ${bList(q)}, command: ${bMap(command)}")
  q << command
  logTrace('addCommandToQueue', "#136 q: ${bList(q)}")
  atomicState.requestQueue = q
  inspectAtomicState('addCommandToQueue', '136')
}

void inspectAtomicState(fn, line) {
  Map m = atomicState
  logTrace('inspectAtomicState', "#${line} ${fn} w/ ${bMap(m)}")
}

Map nextCommandFromQueue() {
  inspectAtomicState('nextCommandFromQueue', '144')
  // Returns a list with the command in position 0 and args 1..N
  ArrayList q = atomicState.requestQueue ?: []
  logInfo('nextCommandFromQueue', "#147 ${bList(q)}")
  Map next = q.removeAt(0)
  logInfo('nextCommandFromQueue', "#148 ${bMap(next)}")
  atmomicState.requestQueue = q
  return(next)
}

void releaseLock() { atomicState.locked = false }

Map coPbsg() {
  // This method:
  //   - Hydrate a PBSG Map from the latest 'jsonPbsg' attribute sendEvent().
  //   - Absent prior history, initialize 'active' (null) and 'lifo' ([]).
  inspectAtomicState('coPbsg', '160')
  Map pbsg = null
  if (!atomicState.locked) {
    atomicState.locked = true
    String curr_jsonPbsg = device.currentValue('jsonPbsg')
    pbsg = curr_jsonPbsg ? fromJson(curr_jsonPbsg) : [ active: null, lifo: [] ]
    pbsg << [priorActive: pbsg.active, priorLifo: pbsg.lifo]
    logTrace('coPbsg', "returning: ${bMap(pbsg)}")
  } else {
    logTrace('coPbsg', 'Blocked by existing atomicState.locked == true')
  }
  return pbsg
}

Map pbsg_CoreKeysOnly(Map pbsg) {
  ArrayList coreKeys = ['active', 'lifo']
  Map corePbsg = pbsg.inject([:]) { m, k, v ->
    if (coreKeys.contains(k)) { m."${k}" = v }
    return m
  }
  //-> logTrace('pbsg_CoreKeysOnly', "returning: ${bMap(corePbsg)}")
  return corePbsg
}

Map ciPbsg() {
  inspectAtomicState('ciPbsg', '185')
  // When an attempt is made to check in a PBSG several things occur:
  //   1. Exit if atomicState.locked is false.
  //   2. Determine the extent of the PBSG change (if any).
  //   3. Refresh child device state to ensure consistency with the PBSG.
  //   4. Adjust attribute states using sendEvent().
  //   5. Adjust priorActive and priorLifo and return the PBSG map.
  if (atomicState.locked) {
    Boolean activeChanged = pbsg.active != pbsg.priorActive
    Boolean lifoChanged = pbsg.lifo != pbsg.priorLifo
    if (activeChanged || lifoChanged) {
      // Refresh/adjust the state of all the PBSG's child devices.
      pbsg.lifo.each{ button -> turnOffVsw(button) }
      pbsg.active && (pbsg.active != 'not_applicable') && turnOnVsw(pbsg.active)
      // Snapshot the core PBSG state
      String jsonPbsg = toJson(pbsg_CoreKeysOnly(pbsg))
      // Send all appropriate events.
      device.sendEvent(
        name: 'jsonPbsg',
        isStateChange: true,
        value: jsonPbsg,
        descriptionText: pbsg_StateText(pbsg)
      )
      /*
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
        device.sendEvent(
          name: 'pushed',
          isStateChange: true,
          value: pushed,
          descriptionText: pushedDesc
        )
      }
      */
      // Update PBSG for prospective next command.
      pbsg.priorActive = pbsg.active
      pbsg.priorLifo = pbsg.lifo
    }
  } else {
    logError('ciPbsg', 'Missing expected atomicState.locked == true')
    pbsg = null
  }
  return pbsg
}

void processCommandQueue(Map pbsg) {
  atomicState.remove('activeCO')
  inspectAtomicState('processCommandQueue', '242')
  // Once a PBSG is obtained with coPbsg():
  //   - This method is called to process all queued commands.
  //   - On completion, releaseLock() is called.
  if (pbsg == null) {
    logError('processCommandQueue', 'Called with null "pbsg" parameter.')
  } else {
    Map command = null
    inspectAtomicState('processCommandQueue', '250')
    while (command = nextCommandFromQueue()) {
      switch(command.name) {
        case 'updateSettings':
          if (settings) {
            if (assertHealthySettings(settings) == 'ALTERED') {
              // Reset PBSG keys and rebuild
              //-> atomicState.remove('requestQueue')
              //-> atomicState.remove('locked')
              inspectAtomicState('processCommandQueue', '259')
              pbsg.active = pbsg.priorActive = null
              pbsg.lifo = pbsg.priorLifo = []
              pbsg_Rebuild(pbsg)
              pruneOrphanedDevices(pbsg)
              ciPbsg(pbsg)
            }
          } else {
            logError('processCommandQueue', ["Command: ${b('updateSettings')}",
              b('No application "settings" were found.')
            ])
          }
          break
        case 'parse':
          // Externally-supplied alternative to settings.
          Map config = settings.findAll { k, v -> (k) }  //  Copies settings
          logTrace('processCommandQueue', ["Command: ${b('parse')}",
            "config (before fromJson): ${bMap(config)}"
          ])
          config << fromJson(command.value)              //  Revise some keys
          logTrace('processCommandQueue', ["Command: ${b('parse')}",
            "config (after fromJson): ${bMap(config)}"
          ])
          if (config) {
            if (assertHealthySettings(config) == 'ALTERED') {
              // Reset PBSG keys and rebuild
              atomicState.remove('requestQueue')
              atomicState.remove('locked')
              inspectAtomicState('processCommandQueue', '287')
              pbsg.active = pbsg.priorActive = null
              pbsg.lifo = pbsg.priorLifo = []
              logTrace('processCommandQueue', ["Command: ${b('parse')}",
                "pbsg (before rebuild): ${bMap(pbsg)}"
              ])
              pbsg_Rebuild(pbsg)
              pruneOrphanedDevices(pbsg)
              ciPbsg(pbsg)
            }
          } else {
            logError('processCommandQueue', ["Command: ${b('parse')}",
              b('No application "config" was found.')
            ])
          }
          break
        case 'activate':
          String button = command.value
          logTrace('activate', button)
          pbsg_ActivateButton(pbsg, button)
          ciPbsg(pbsg)
          break
        case 'deactivate':
          String button = command.value
          logTrace('deactivate', button)
          pbsg_DeactivateButton(pbsg, button)
          ciPbsg(pbsg)
          break
        case 'toggle':
          String button = command.value
          if (pbsg.active == button) {
            logTrace('processCommandQueue', "Push toggling ${button} off.")
            pbsg_DeactivateButton(pbsg, button)
          } else {
            logTrace('processCommandQueue', "Push toggling ${button} on.")
            pbsg_ActivateButton(pbsg, button)
          }
          break
        //-> case 'refreshVsws':
        //->   // Support child VSW refresh in isolation for system restart, etc.
        //->   break
      }
    }
    releaseLock()
  }
}

String assertHealthySettings(Map settings) {
  // This method applies settings.logLevel in isolation if healthy.
  // This method returns:
  //      'ISSUES': One or more issues were reported to the Hubitat log.
  //   'UNCHANGED': Settings are healthy AND unchanged.
  //     'ALTERED': Settings are healthy AND altered, requiring a PBSG rebuild.
  String result
  ArrayList issues = []
  if (settings == null) {
    issues << "Argument ${b('settings')} is null."
  } else {
    if (settings.logLevel == null) {
      issues << "Preference ${b('logLevel')} is null."
    } else {
      setLogLevel(settings.logLevel)
    }
    if (settings.logVswActivity == null) {
      issues << "Preference ${b('logVswActivity')} is null."
    }
    if (settings.buttons == null) {
      issues << "Preference ${b('buttons')} is null."
    }
    String markDirty = buttons?.replaceAll(/[\W_&&[^_ ]]/, '▮')
    if (buttons != markDirty) {
      issues << [
        "Invalid ${b('buttons')} string:",
        buttons,
        "${markDirty} ('▮' denotes problematic characters)",
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
    }
    ArrayList prospectiveButtonsList = spaceDelimitedButtons.tokenize(' ')
    Integer prospectiveButtonsCount = prospectiveButtonsList.size()
    if (prospectiveButtonsCount < 2) {
      issues << [
        'Two buttons are required to proceed:',
        "Found ${prospectiveButtonsCount} buttons: ${bList(prospectiveButtonsList)}"
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
    }
    if (settings.dflt == null) {
      issues << "Preference ${b('dflt')} is null (expected 'not_applicable')"
    }
    if (! [prospectiveButtonsList.contains(settings.dflt)]) {
      issues << [
        "Preference ${b('dflt')} (${settings.dflt}) not present among ",
        "buttons: ${bList(prospectiveButtonsList)}"
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
    }
    if (settings.instType == null) {
      issues << "Preference ${b('instType')} is null."
    }
  }
  if (issues) {
    result = 'ISSUES'
    logError('assertHealthySettings', [ 'Known issues:', *issues ])
  } else {
    result = (
      prospectiveButtonsList != atomicState.buttonsList
      || settings.dflt != atomicState.dflt
      || settings.instType != atomicState.instType
    ) ? 'ALTERED' : 'UNCHANGED'
    if (result == 'ALTERED') {
      // Normalize settings, update atomicState and update attributes
      ArrayList oldButtonsList = atomicState.buttonsList
      Integer oldButtonCount = oldButtonsList.size()
      atomicState.buttonsList = prospectiveButtonsList
      device.updateSetting(
        'buttons',
        [value: prospectiveButtonsList.join(' '), type: 'String']
      )
      Integer oldButtonsCount = oldButtonsList.size()
    if (oldButtonsCount != prospectiveButtonsCount) {
      String desc = "${i(oldButtonsCount)} → ${b(prospectiveButtonsCount)}"
      logTrace(
        'updatedButtonsString',
        "Updating attribute ${b('numberOfButtons')}: ${desc}"
      )
      device.sendEvent(
        name: 'numberOfButtons',
        isStateChange: true,
        value: prospectiveButtonsCount,
        unit: '#',
        descriptionText: desc
      )
    }
    }
  }
  return result
}

//-> void refreshVsws() {
//->   // Support child VSW refresh in isolation for system restart, etc.
//-> }

//// DEVICE LIFECYCLE METHODS
////   - These methods are called by Hubitat as explained below.

void installed() {
  // Called when a bare device is first constructed.
  // Note: Settings ARE NOT likely to meet sufficiency requirements.
  settings?.logLevel && setLogLevel(settings.logLevel)
  logWarn('installed', 'Checking sufficiency of current settings')
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
  logTrace('refresh', 'Checking sufficiency of current settings')
  ifSufficientSettingsConfigure('refresh()')
}

void updated() {
  // Called when a human uses the Hubitat GUI's Device drilldown page to edit
  // preferences (aka settings) AND presses 'Save Preferences'.
  logTrace('updated', 'Checking sufficiency of current settings')
  ifSufficientSettingsConfigure('updated()')
}

void configure() {
  // Callable on demand per capability 'Configuration'.
  // TBD: Migrate to Command Queue
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

/*
  name: 'config',
  value: "${jsonConfigMap}",
  descriptionText: "Updated config map ${jsonConfigMap}",
  isStateChange: null  // queued command, n/a

  name: 'activate',
  value: "${button}",
  descriptionText: "Activate Button ${button}",
  isStateChange: null  // queued command, n/a

  name: 'deactivate',
  value: "${button}",
  descriptionText: "Deactivate Button ${button}",
  isStateChange: null  // queued command, n/a

  name: 'testVswOn',
  value: "${button}",
  descriptionText: "Simulate ${getVswForButton(button)} turned on",
  isStateChange: null  // test action, n/a

  name: 'testVswOff',
  value: "${button}",
  descriptionText: "Simulate ${getVswForButton(button)} turned off",
  isStateChange: null  // test action, n/a

  name: 'testVswPush',
  value: "${button}",
  descriptionText: "Simulate ${getVswForButton(button)} pushed",
  isStateChange: null  // test action, n/a
*/

void parse(ArrayList commands) {
  atomicState.remove('activeCO')
  atomicState.remove('requestQueue')
  inspectAtomicState('parse(ArrayList)', '#516')
  logTrace('parse(ArrayList)', "commands: ${commands}")
  // Accepts an ArrayList of high-level commands.
  commands.each { command ->
  logTrace('parse(ArrayList)', "command (loop): ${command}")
    // Some commands are added to the atomicState.requestQueue.
    // The test commands (which simulate externally-driven VSW state changes)
    // are forwarded to the appropriate VSW.
    //   - These changes cause the VSW to take actions that (inevitably)
    //     queue actions in the atomicState.requestQueue.
    if (['config', 'activate', 'deactivate'].contains(command.name)) {
      logTrace('parse(ArrayList)', "command: ${bMap(command)}")
      addCommandToQueue(command)
    } else if (['testVswOn', 'testVswOff', 'testVswPush'].contains(command.name)) {
      DevW d = getVswForButton(command.value)
      String operation = command.name.substring(7)
      logTrace('parse(ArrayList)', "Performing ${b(operation)} on ${b(d.getDeviceNetworkId())}")
      d."${operation}"
    } else {
      logWarn('parse(ArrayList)', "Unsupported command: ${bMap(command)}")
    }
  }
}

void turnOnVsw(String button) {
  ChildDevW d = getVswForButton(button)
  if (d) {
    // Parse expects a list (ArrayList) of commands (Maps)
    ArrayList commands = [] << [
      name: 'switch',
      value: 'on',
      descriptionText: "Turned ${b('on')} ${devHued(d)}",
      isStateChange: (d.switch != 'on')
    ]
    d.parse(commands)
  }
}

void turnOffVsw(String button) {
  if (button) {
    ChildDevW d = getVswForButton(button)
    if (d) {
      // Parse expects a list (ArrayList) of commands (Maps)
      ArrayList commands = [] << [
        name: 'switch',
        value: 'off',
        descriptionText: "Turned ${b('off')} ${devHued(d)}",
        isStateChange: (d.switch != 'off')
      ]
      d.parse(commands)
    }
    if (settings?.logVswActvity) {
      logInfo('turnOffVsw', "Turned off ${d.getDeviceNetworkId()}")
    }
  } else {
    logError('turnOffVsw', 'Received null parameter "button"')
  }
}

String currentSettingsHtml() {
  return [
    b('SETTINGS:'),
    settings.collect { k, v -> "${i(k)}: ${b(v)}" }.join(', ')
  ].join('<br/>')
}

void pbsg_Rebuild(Map pbsg) {
  inspectAtomicState('pbsg_Rebuild', '600')
  logTrace('pbsg_Rebuild at start', "pbsg: ${pbsg}")
  // Return true if rebuild is successful.
  ArrayList buttonsList = atomicState.buttonsList
  buttonsList.each { button ->
    ChildDevW vsw = getOrCreateVswWithToggle(device.getLabel(), button)
    pbsg.lifo.push(button)
    if (vsw.switch == 'on') {
      // Move the button from the LIFO to active
      pbsg_ActivateButton(pbsg, button)
    }
    logTrace('rebuildPbsg', pbsg_StateHtml(pbsg))
  }
  if (!pbsg.active && settings.dflt && settings.dflt != 'not_applicable') {
    pbsg_EnforceDefault(pbsg)
  }
  logTrace('pbsg_Rebuild at end', "pbsg: ${pbsg}")
}

void pruneOrphanedDevices(Map pbsg) {
  ArrayList buttonsList = atomicState.buttonsList
  ArrayList expectedChildDnis = buttonsList.collect { button ->
    "${device.getLabel()}_${button}"
  }
  ArrayList currentChildDnis = getChildDevices().collect { d ->
    d.getDeviceNetworkId()
  }
  ArrayList orphanedDevices = currentChildDnis?.minus(expectedChildDnis)
  orphanedDevices.each { dni ->
    logWarn('rebuildPbsg', "Removing orphaned device ${b(dni)}.")
    deleteChildDevice(dni)
  }
}

Map pbsg_ActivateButton(Map pbsg, String button) {
  // Make 'button' active (if not already) in pbsg.
  if (pbsg?.active == button) {
    logTrace(
      'pbsg_ActivateButton',
      "${b(button)} was already activated"
    )
  } else if (!pbsg?.lifo?.contains(button)) {
    logWarn(
      'pbsg_ActivateButton',
      "${b(button)} not found in lifo (${bList(pbsg.lifo)})"
    )
  } else {
    // Push any currently active button into lifo.
    if (pbsg.active) {
//->logInfo('pbsg_ActivateButton', "Before moving active to lifo:<br/>${pbsg}")
      //pbsg.lifo.putAt(0, pbsg.active)
      pbsg.lifo.push(pbsg.active)
//->logInfo('pbsg_ActivateButton', "After moving active to lifo:<br/>${pbsg}")
    }
    // Remove target button from lifo and make it active.
    pbsg.lifo.removeAll([button])
    pbsg.active = button
//->logInfo('pbsg_ActivateButton', "After moving ${button} from lifo to active:<br/>${pbsg}")
    //logTrace('pbsg_ActivateButton', "${b(button)} ⟹ ${bMap(pbsg)}")
//logInfo('pbsg_ActivateButton', ">${pbsg.active}< >${pbsg.lifo}< after ${button} lifo->active")
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
    logTrace('pbsg_DeactivateButton', "${b(button)} ⟹ ${bMap(pbsg)}")
  }
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

String pbsg_StateHtml(Map pbsg) {
  // IMPORTANT:
  //   LIFO push() and pop() are supported, *BUT* pushed items are appended
  //   (not prepended). See "reverse()" below, which compensates.
  String result
  if (pbsg) {
    ArrayList list = []
    list << "${devHued(device)}: "
    list << pbsg.active ? buttonState(pbsg.active) : 'null'
    list << ' ← '
    pbsg.lifo?.reverse().each { button ->
    //pbsg.lifo?.each { button ->
      list << buttonState(button)
    }
    list << "<b>LIFO</b>"
    result = list.join()
  } else {
    result = "${devHued(device)}: null"
  }
  return result
}

String pbsg_StateText(Map pbsg) {
  // IMPORTANT:
  //   LIFO push() and pop() are supported, *BUT* pushed items are appended
  //   (not prepended). See "reverse()" below, which compensates.
  String result
  if (pbsg) {
    ArrayList table = ''
    table << '<span style="display:inline-table;">'
    table << '<table><tr>'
    table << "<td>${devHued(device)}:&nbsp;</td>"
    table << tdBordered( pbsg.active ? buttonState(pbsg.active) : 'null' )
    table << '<td>&nbsp;←&nbsp;</td>'
    pbsg.lifo?.reverse().each { button ->
    //pbsg.lifo?.each { button ->
      table << tdBordered( buttonState(button) )
    }
    table << "<td><b>LIFO</b></td></tr></table></span>"
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
      'WesMC',               // namespace
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

//// VSW SUPPORT

String getButtonForVsw(DevW d) {
  return d.getDeviceNetworkId().tokenize('_')[1]
}

ChildDevW getVswForButton(String button) {
  String dni = "${device.getLabel()}_${button}"
  ChildDevW d = getChildDevice(dni)
  if (!d) {
    logError('getVswForButton', "No Device (${devHued(d)}) for button (${b(button)}).")
  }
  return d
}

//// SUPPORT FOR CAPABILITY 'PushableButton'

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

void parse(String) {
  // This method is reserved for interaction with FUTURE parent devices.
  logError('parse(String)', 'Called unexpectedly')
}
