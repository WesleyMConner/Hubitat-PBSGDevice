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

//// CONFLICT MANAGEMENT
////   - The owning parent can call - parse(), activate(), deactivate(), ....
////   - External parties (e.g., Alexa) can adjust vsws - on(), off(), push().
////   - To reduce (but not eliminate) the risk of conflicting (interleaved)
////     actions:
////       - State-changing actions are serialized in 'atomicState.fifo'
////         using minimalist methods.
////       - The semaphore 'atomicState.locked' is 'true' when a process is
////         adjusting state - preventing other competing state changes.

metadata {
  definition(
    name: 'PBSG',
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    description: """A virtual PushButtonSwitchGroup (PBSG) device manages two
or more wesmc.VswWithToggle child devices which are mutually-exclusive 'on'.
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
    ////       - The following commands give wesmc.PBSG Client Apps an easy way
    ////         to simulate external actions in VswWithToggle child devices.
    ////       - Without these commands:
    ////           - Parent Apps (e.g., Demo-PBSG.groovy) can only access child
    ////             devices (e.g., instances of wesmc.PBSG). Parent Apps cannot
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
  //// PREFERENCES
  ////   - MANUAL adjustments are made via Hubitat's GUI Device drilldown page.
  ////       - Use of type 'Enum'
  ////           - Per Mike Maxwell, "Enum inputs in drivers ARE NOT DYNAMIC"
  ////             https://community.hubitat.com/t/driver-commands-with-enum/2110/5
  ////           - Enum preferences CANNOT BE UPDATED via device.updateSetting().
  ////           - These constraints
  ////   - Enums must be fixed (see logLevel below).
  ////   - Neil Anderson suggests using a custom command instead.
  ////     https://community.hubitat.com/t/driver-commands-with-enum/2110/11
  ////   - Below, Preference 'dflt' is of type 'String' so that it operates
  ////     symmetrically alongside other Preferences - e.g., see parms().
  ////   - PROGRAMMATIC adjustments are available to the parent App via
  ////     custom code in 'parse(String json)' - see below.
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
      defaultValue: 'INFO',
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

//// FIFO Management
////   - All state-changing actions are serialized into 'atomicState.fifo'
////     using addToQueue(String command).
////       - Simple commands have zero or more ^-delimited String arguments:
////           "${command}^${argString1}^${argString2}^...^${argN}"
////       - Complex commands serialize arguments using JSON:
////           "${command}^${jsonString}"
////   - The semaphore 'atomicState.locked' is 'true' when a process is
////     adjusting state - blocking competing state changes.

void addCommandToQueue(String command) {
  ArrayList q = atomicState.requestQueue ?: []
  atmomicState.requestQueue = q.push(command)
  ifLogDebug() {
    ArrayList queued = ['']
    queued = q?.eachWithIndex() { l, i -> queued.add("${i}: ${l}") }
    logDebug('addCommandToQueue', queued)
  }
}

ArrayList nextCommandFromQueue() {
  // Returns a list with the command in position 0 and args 1..N
  ArrayList q = atomicState.requestQueue ?: []
  ArrayList next = q.removeAt(0).split('^')
  atmomicState.requestQueue = q
  return(next)
}


void releaseLock() { atomicState.locked = false }

Map coPbsg() {
  // This method:
  //   - Hydrate a PBSG Map from the latest 'jsonPbsg' attribute sendEvent().
  //   - Absent prior history, initialize 'active' (null) and 'lifo' ([]).
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
  // Once a PBSG is obtained with coPbsg():
  //   - This method is called to process all queued commands.
  //   - On completion, releaseLock() is called.
  if (pbsg == null) {
    logError('processCommandQueue', 'Called with null "pbsg" parameter.')
  } else {
    ArrayList command
    while (command = nextCommandFromQueue()) {
      switch(command[0]) {
        case 'updateSettings':
          if (settings) {
            applyAdjustedSettings(pbsg, settings)
          } else {
            logError('processCommandQueue', ["Command: ${b('updateSettings')}",
              b('No application "settings" were found.')
            ])
          }
          // Ensure logging preferences exist and are applied (w/out rebuild)
          if (settings?.logLevel == null) {
            logError('processCommandQueue', 'Preference for logLevel is null.')
          } else {
            setLogLevel(settings.logLevel)
          }
          if (settings?.logVswActivity == null) {
            logError('processCommandQueue', 'Preference for logVswActivity is null.')
          }
          // If any of the following have changed (return true), rebuild PBSG.
          if (revisedHealthyButtonsString(settings.buttons)
            || revisedHealthyDfltString(settings.dflt)
            || revisedHealthyInstType(settings.instType)
          ) {
            // Reset all keys in the checked out PBSG to 'start from scratch'
            pbsg.active = null
            pbsg.lifo = []
            pbsg.priorActive = null
            pbsg.priorLifo = []
          }
          break;
        //-> case 'refreshVsws':
        //->   // Support child VSW refresh in isolation for system restart, etc.
        //->   break;
        case 'parse':
          // Process externally-supplied alternative to settings.
          break;
        case 'activate':
          String button = command[1]
          logTrace('activate', button)
          Map pbsg = coPbsg()
          pbsg_ActivateButton(pbsg, button)
          ciPbsg(pbsg)
          break;
        case 'deactivate:'
          String button = command[1]
          logTrace('deactivate', button)
          pbsg = coPbsg()
          pbsg_DeactivateButton(pbsg, button)
          ciPbsg(pbsg)
          break;
        case 'toggle'
          String button = command[1]
          if (pbsg.active == button) {
            logTrace('processCommandQueue', "Push toggling ${button} off.")
            pbsg_DeactivateButton(pbsg, button)
          } else {
            logTrace('processCommandQueue', "Push toggling ${button} on.")
            pbsg_ActivateButton(pbsg, button)
          }
          break;
      }
    }
    releaseLock()
  }
}

void applyAdjustedSettings(Map pbsg, Map settings) {
  // Ensure logging preferences exist and are applied (w/out rebuild)
  if (settings.logLevel == null) {
    logWarn('applyAdjustedSettings', "Missing ${b('settings.logLevel')}")
  } else {
    setLogLevel(settings.logLevel)
  }
  if (settings?.logVswActivity == null) {
    logWarn('processCommandQueue', "Missing ${b('settings.logVswActivity')}")
  }
  // If any of the following have changed (i.e., return true), rebuild the PBSG.
  if (revisedHealthyButtonsString(settings.buttons)
   || revisedHealthyDfltString(settings.dflt)
   || revisedHealthyInstType(settings.instType)
  ) {
    // Without releasing the lock, edit the PBSG Map (passed by reference)
    // to (effectively) "start from scratch"



    rebuildPbsg()
  }
}

Boolean revisedHealthyButtonsString(String spaceDelimitedButtons) {
  // This method:
  //   - Logs ANY issues with the provided string and immediately returns FALSE.
  //   - If the provided string meets sufficiency requirements AND represents
  //     a change in the buttons OR their order:
  //       - Normalize settings.buttons (buttons delimited with a single space)
  //       - Update the ArrayList 'atomicState.buttonsList'
  //       - Update the attribute 'numberOfButtons' if changed.
  //       - Return TRUE indicating that a PBSG rebuilt is required.
  Boolean revisedButtons = false
  String markDirty = buttons?.replaceAll(/[\W_&&[^_ ]]/, '▮')
  ArrayList newButtonsList = spaceDelimitedButtons.tokenize(' ')
  Integer newButtonsCount = newButtonsList.size()
  ArrayList oldButtonsList = atomicState.buttonsList
  if (spaceDelimitedButtons == null) {
    logError('revisedHealthyButtonsString', ['',
      "Argument 'spaceDelimitedStrings' is null.",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if (buttons != markDirty) {
    logError('updatedButtonsString', ['',
      "Invalid ${b('buttons')} string:",
      buttons,
      "${markDirty} ('▮' denotes problematic characters)",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if (newButtonsCount < 2) {
    logError('revisedHealthyButtonsString', ['',
      'Two buttons are required to proceed',
      "Found ${newButtonsCount} buttons: ${buttonsList.join(', ')}",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if (! newButtonsList.equals(oldButtonsList)) {
    // Sufficiency requirements are met AND the buttons list has changed.
    atomicState.buttonsList = buttonsList
    device.updateSetting('buttons', [value: buttonsList.join(' '), type: 'String'])
    Integer oldButtonsCount = oldButtonsList.size()
    if (newButtonsCount != newButtonsCount) {
      String desc = "${i(oldButtonsCount)} → ${b(newButtonsCount)}"
      logTrace(
        'updatedButtonsString',
        "Updating attribute ${b('numberOfButtons')}: ${desc}"
      )
      device.sendEvent(
        name: 'numberOfButtons',
        isStateChange: true,
        value: newButtonsCount,
        unit: '#',
        descriptionText: desc
      )
    }
    revisedButtons = true
  }
  return revisedButtons
}

Boolean revisedHealthyDfltString(String dflt) {
  // This method:
  //   - Ensures 'dflt' (if provided) is a legitimate button.
  //   - Preserves the working default button as 'atomicState.dfltButton'.
  //   - When the default is revised, this method returns TRUE (indicating a
  //     PBSG rebuild may be required) otherwise it returns FALSE.
  Boolean revisedDflt
  String newDflt = (dflt == 'not_applicable') ? null : dflt
  ArrayList buttonsList = atomicState.buttonsList
  if (newDflt && (! buttonsList?.contains(newDflt))) {
    logError(
      'revisedHealthyDfltString',
      "The settings.dflt (${b(dflt)}) is not found in buttons (${bList(buttonsList)})"
    )
  } else if (newDflt != atomicState.dfltButton) {
    atomicState.dfltButton = newDflt
    revisedDflt = true
  }
  return revisedDflt
}

Boolean revisedHealthyInstType(String instType) {
  // This method:
  //   - The String 'instType' exists to differentate prospective descendants
  //     of PBSG (as a pseudo-Class). Other than scanning for illadvised
  //     characters, offered Strings are accepted essentially as-is.
  //   - As the 'instType' may matter if future applications, changes are
  //     registered into atomicState and applied to the current PBSG (without
  //     fanfare). There is no attribute publication for instType.
  revisedInstType = false
  String markDirty = instType?.replaceAll(/[\W_&&[^_]]/, '▮')
  String oldInstType = atomicState.instType
  if (instType != markDirty) {
      logError('revisedHealthyInstType', ["Invalid ${b('instType')} preference:",
      instType,
      "${markDirty} ('▮' denotes problematic character).",
      'If a PBSG exist, it is left AS IS.'
    ])
  } else if {instType != oldInstType} {
    atomicState.instType = instType
    revisedInstType = true
  }
  return revisedInstType
}

Boolean revisedHealthyLogLevel(String logLevel) {
  // This method:

  result = false
  ArrayList options = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']
  if (!options.contains(logLevel)) {
    logError(
      'revisedHealthyLogLevel',
      "logLevel (${b(logLevel)}) not in ${bList(options)}."
    )
  } else {
    device.updateSetting('logLevel', [value: logLevel, type: 'Enum'])
    settings?.logLevel && setLogLevel(settings.logLevel)
    result = true
  }
  return result
}

void refreshVsws() {
        // Support child VSW refresh in isolation for system restart, etc.
}

void parse() {
        // Process externally-supplied alternative to settings.
}

void activate(String button) {
}

void deactivate(String button) {
        String button = command[1]
        logTrace('deactivate', button)
        pbsg = coPbsg()
        pbsg_DeactivateButton(pbsg, button)
        ciPbsg(pbsg)
        break;
}

void toggle(String button) {
}


// DISPATCHED CHILD TEST ACTIONS ON ARRIVAL
//   - The resulting client actions are queued for execution.

void testVswOn(String button) {
  logInfo('testVswOn', 'Simulating an external vswWithToggle.on()')
  getVswForButton(button).on()
}

void testVswOff(String button) {
  logInfo('testVswOn', 'Simulating an external vswWithToggle.off()')
  getVswForButton(button).off()
}

void testVswPush(String button) {
  logInfo('testVswOn', 'Simulating an external vswWithToggle.push()')
  getVswForButton(button).push()
}




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

// REVIEW !!!
void configure() {
  // Callable on demand per capability 'Configuration'.
  if (
    revisedHealthyButtonsString(settings.buttons)
    && revisedHealthyDfltString(settings.dflt)
    && revisedHealthyInstType(settings.instType)
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
  //              buttons: <String>,  // Adjusts settings.buttons
  //                 dflt: <String>,  // Leverages setDefaultButton()
  //             instType: <String>,  // Adjusts settings.instType
  //       logVswActivity: <Booelan>  // Adjusts settings.logLevel
  //     ])
  Map parms = fromJson(jsonConfig)
  logTrace('parse', "Received parms: ${bMap(parms)} (from Json)")
  if (
    revisedHealthyButtonsString(parms.buttons ?: settings.buttons)
    && revisedHealthyDfltString(parms.dflt ?: settings.dflt)
    && revisedHealthyInstType(parms.instType ?: settings.instType)
    && revisedHealthyLogLevel(parms.logLevel ?: settings.logLevel)
    && healthyLogVswActivity(parms.logVswActvity ?: settings.logVswActvity)
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

  if (atomicState.taskList.size())
  atomicState.taskList == 'REBUILDING'
  Map pbsg =
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
    logTrace('rebuildPbsg', pbsg_StateHtml(pbsg))
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
  atomicState.taskList == 'INACTIVE'
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

void parse(ArrayList actions) {
  // This method is reserved for interaction with FUTURE parent devices.
  logWarn('parse', ['parse(ArrayList) ignored:',
    actions.join('<br/>')
  ], '<br/>')
}
