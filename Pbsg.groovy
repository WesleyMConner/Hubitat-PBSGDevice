/* P ( U S H )   B ( U T T O N )   S ( W I T C H )   G ( R O U P )
 *
 * Copyright (C) 2023-Present Wesley M. Conner
 *
 * LICENSE
 * Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
 * "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
 * not use this file except in compliance with the License. Unless
 * required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 */

// WesMC.lUtils
//   - The imports below support library methods.
//   - Expect a Groovy Linter 'NglParseError' per Hubitat #include.
#include WesMC.lUtils
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput as JsonOutput
import groovy.json.JsonSlurper as JsonSlurper
import java.lang.Math as Math
import java.lang.Object as Object

// Imports specific to this file.
import groovy.transform.Field
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue

@Field static ConcurrentHashMap<Long, Map> STATE = [:]
@Field static ConcurrentHashMap<Long, SynchronousQueue> QUEUE = [:]

Long DID() { return device.idAsLong }

metadata {
  definition(
    name: 'PBSG',
    namespace: 'WesMC',
    author: 'Wesley M. Conner',
    description: """Virtual PushButtonSwitchGroup (PBSG) Device""",
    category: '',   // As of Q2'24 Not used
    iconUrl: '',    // As of Q2'24 Not used
    iconX2Url: '',  // As of Q2'24 Not used
    documentationLink: 'A Hubitat Community post is pending',
    importUrl: 'https://github.com/WesleyMConner/Hubitat-PBSGLibrary',
    singleThreaded: 'false',
  ) {
    capability 'Initialize'      // Commands: initialize()
    capability 'PushableButton'  // Attributes:
                                 //   - numberOfButtons: number
                                 //   - pushed: number
                                 // Commands: push(number)

    // Commands not implied by a Capability
    command 'config', [
      [ name: 'jsonPrefs', type: 'String', description: 'Map of prefs serialized as JSON']
    ]
    command 'activate', [
      [ name: 'button', type: 'text', description: 'button to activate' ],
      [ name: 'description', type: 'text', description: 'optional text' ]
    ]
    command 'deactivate', [
      [ name: 'button', type: 'text', description: 'button to deactivate' ],
      [ name: 'description', type: 'text', description: 'optional text' ]
    ]
    command 'testVswOn', [
      [ name:'button', type: 'text', description: 'TEST vsw.on()' ],
      [ name: 'description', type: 'text', description: 'optional text' ]
    ]
    command 'testVswOff', [
      [ name:'button', type: 'text', description: 'TEST vsw.off()' ],
      [ name: 'description', type: 'text', description: 'optional text' ]
    ]
    command 'testVswPush', [
      [ name:'button', type: 'text', description: 'TEST vsw.push()' ],
      [ name: 'description', type: 'text', description: 'optional text' ]
    ]
    // Attributes not implied by a Capability
    attribute 'jsonPbsg', 'string'
    attribute 'active', 'string'
  }
  preferences {
    input( name: 'buttons',
      title: "${b('Button Names')} (space delimited)",
      type: 'text',
      required: true
    )
    input( name: 'dflt',
      title: [
        b('Default Button'),
        i('(Select a Button Name or "not_applicable")')
      ].join('<br/>'),
      type: 'text',  // Cannot be an Enum since buttons (are dynamic).
      multiple: false,
      defaultValue: 'not_applicable',
      required: false
    )
    input( name: 'instType',
      title: b('Type of PBSG'),
      type: 'text',
      defaultValue: 'pbsg',
      required: true
    )
    input( name: 'logLevel',
      title: b('PBSG Log Threshold ≥'),
      type: 'enum',
      multiple: false,
      options: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],  // Static Options
      defaultValue: 'TRACE',
      required: true
    )
    input( name: 'logVswActivity',
      title: b('Enable/Disable VSW Logging'),
      type: 'bool',
      defaultValue: true,
      required: true
    )
  }
}

// State Management Methods

Map getEmptyPbsg() {
  return [
    version: java.time.Instant.now(),
    buttonsList: [],
    dflt: null,
    instType: 'pbsg',
    active: null,
    lifo: []
  ]
}

void installed() {
  // Called when a bare device is first constructed.
  logWarn('installed', 'Initializing STATE and QUEUE PBSG entries.')
  STATE[DID()] = getEmptyPbsg()
  QUEUE[DID()] = new SynchronousQueue(true)
}

void uninstalled() {
  // Called on device tear down.
  logWarn('uninstalled', 'Removing STATE and QUEUE entries.')
  STATE.remove(DID())
  QUEUE.remove(DID())
}

void initialize() {
  // Called on hub startup (per capability "Initialize").
  logTrace('initialize', 'Initializing STATE and QUEUE PBSG entries.')
  Map pbsg = getEmptyPbsg()
  STATE[DID()] = pbsg
  QUEUE[DID()] = new SynchronousQueue(true)
  logTrace('initialize', 'Attempting PBSG Rebuild from configuration data.')
  updatePbsgStructure(ref: 'initialize')
}

void updated() {
  // Called when a human uses the Hubitat GUI's Device drilldown page to edit
  // preferences (aka settings) AND presses 'Save Preferences'.
  //   - If (and only if) devices settings have changed, the PBSG is rebuilt.
  //   - On PBSG rebuild, anything pending in the commandQueue is dropped.
  logTrace('updated', 'Attempting PBSG Rebuild from configuration data.')
  updatePbsgStructure(parms: [ref: 'updated'])
}

// UTILITY METHODS

Integer buttonNameToPushed(String button, ArrayList buttons) {
  // Button name to button 'keypad' position is always computed 'on-the-fly'.
  return buttons?.withIndex().collectEntries { b, i ->
    [(b), i+1]
  }?."${button}"
}

// STATE ALTERING COMMANDS

void config(String jsonPrefs, String ref = '') {
  // If a config change alters the structure of the PBSG:
  //   - The PBSG is rebuilt to the new structure
  //   - The PBSG version is updated.
  //   - The cmdQueueHandle is bounced and targets the commands for
  //     the new version.
  updatePbsgStructure(config: jsonPrefs, ref: 'updated')
}

void activate(String button, String ref = '') {
  Map command = [
    name: 'Activate',
    arg: button,
    ref: ref,
    version: STATE[DID()].version
  ]
  logTrace('activate', "Queing ${bMap(command)}")
  // At a later date, offer vs put.
  QUEUE[DID()].put(command)
}

void deactivate(String button, String ref = '') {
  Map command = [
    name: 'Deactivate',
    arg: button,
    ref: ref,
    version: STATE[DID()].version
  ]
  logTrace('deactivate', "Queing ${bMap(command)}")
  QUEUE[DID()].put(command)
}

void toggle(String button, String ref = '') {
  Map command = [
    name: 'Toggle',
    arg: button,
    ref: ref,
    version: STATE[DID()].version
  ]
  logTrace('toggle', "Queing ${bMap(command)}")
  QUEUE[DID()].put(command)
}

void testVswOn(String button, String ref = '') {
  logTrace('testVswOn', "Executing device.on() for ${b(button)}.")
  getVswForButton(button).on()
}

void testVswOff(String button, String ref = '') {
  logTrace('testVswOff', "Turning device.off() for ${b(button)}.")
  getVswForButton(button).off()
}

void testVswPush(String button, String ref = '') {
  logTrace('testVswPush', "Executing device/push() for ${b(button)}.")
  getVswForButton(button).push()
}

//// STATE ALTERING METHODS

void updatePbsgStructure(Map parms) {
  // Abstract
  //   * Evaluates the health of config (settings overlayed with parms.conf).
  //   * If config is healthy and the PBSG STRUCTURE has changed:
  //     ** Updates the PBSG Version
  //     ** Rebuilds the PBSG's DYNAMIC keys.
  //     ** Unschedules the cmdQueueHandler.
  //     ** Updates STATE
  //     ** Re-schedules the cmdQueueHandler (focused on the new Version)
  // Input
  //   parms.config - Map of <k, v> pairs that overwrite settings <k, v> pairs.
  //      parms.ref - Context string provided by caller
  // Output
  //   null - Config is unhealthy or unchanged relative to CSM, see logs.
  //    Map - The new PBSG (as saved to STATE).
  Map config = settings   // Insert Preference <k, v> pairs.
  config << parms.config  // Overlay provided <k, v> pairs (from parms).
  ArrayList issues = []   // Accumulate any config issues.
  if (config) {
    // Process the (two) log fields which ARE NOT part of STATE.
    if (config.logLevel == null) {
      issues << "Missing config ${b('logLevel')}, defaulting to TRACE."
      updateSetting(logLevel, 'TRACE')
      setLogLevel('TRACE')
    } else if (!['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].contains(config.logLevel)) {
      issues << "Unrecognized config ${b('logLevel')} '${config.logLevel}', defaulting to TRACE."
      updateSetting(logLevel, 'TRACE')
      setLogLevel('TRACE')
    } else {
      setLogLevel(config.logLevel)
    }
    if (config.logVswActivity == null) {
      issues << "The setting ${b('logVswActivity')} is null, forcing TRUE"
      updateSetting(logVswActivity, true)
    }
    // Reviewing PBSG Structural fields.
    Boolean healthyButtons = true
    String markDirty = config?.buttons?.replaceAll(/[\W_&&[^_ ]]/, '▮')
    ArrayList buttonsList = config?.buttons?.tokenize(' ')
    Integer buttonsCount = buttonsList?.size()
    if (config.buttons == null) {
      issues << "The setting ${b('buttons')} is null."
      healthyButtons = false
    } else if (config.buttons != markDirty) {
      issues << [
        "The setting ${b('buttons')} has invalid characters:",
        config.buttons,
        "${markDirty} ('▮' denotes problematic characters)",
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
      healthyButtons = false
    }
    if (buttonsCount < 2) {
      issues << [
        'Two buttons are required to proceed:',
        "Found ${buttonsCount} buttons: ${bList(buttonsList)}"
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
      healthyButtons = false
    }
    // Normalize settings.buttons (e.g., reduce excess whitespace)
    if (healthyButtons) { updateSetting('buttons', buttonsList.join(' ')) }
    if (config.dflt == null) {
      issues << "The setting ${b('dflt')} is null (expected 'not_applicable')"
    }
    if (! [prospectiveButtonsList.contains(config.dflt)]) {
      issues << [
        "The setting ${b('dflt')} (${config.dflt}) is not found among ",
        "buttons: ${bList(prospectiveButtonsList)}"
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
    }
    if (config.instType == null) {
      issues << "The setting ${b('instType')} is null."
    }
  } else {
    issues << 'No Preferences/settings or parms.config map was found.'
  }
  if (issues) {
    // REPORT THE DISCOVERED ISSUES AND STOP.
    logError('updatePbsgStructure', [ parms.ref,
      'The following known issues prevent a PBSG (re-)build at this time',
      *issues
    ])
  } else {
    // Does the (healthy) configuration merits a PBSG rebuild?
    String dflt = (config.dflt == 'not_applicable') ? null : config.dflt
    Boolean structureAltered = (
      config.buttonsList != STATE[DID()].buttonsList
      || dflt != STATE[DID()].dflt
      || config.instType != STATE[DID()].instType
    )
    if (structureAltered) {
      logWarn('updatePbsgStructure', [ parms.ref,
        'The PBSG structure has changed, necessitating a PBSG rebuild.'
      ])
      // Populate newPbsg with Structural fields only.
      Map newPbsg = [
        version: java.time.Instant.now(),
        buttonsList: buttonsList,
        dflt: dflt,
        instType: 'pbsg',
        active: null,
        lifo: []
      ]
      Map pbsg = populateNewPbsg(newPbsg: newPbsg, ref: parms.ref)
    } else {
      logWarn('newPbsg', [ parms.ref,
        'The PBSG is healthy and DOES NOT REQUIRE a rebuild'
      ])
    }
  }
}

Map populateNewPbsg(Map parms) {
  // Abstract
  //   Rebuild the PBSG per the revised structure of the PBSG and iteratively
  //   process the Command Queue.
  // Input
  //   parms.newPbsg - Initialized PBSG Map w/ structural fields populated.
  //       parms.ref - Context string provided by caller
  // Output
  //   null - Rebuild failed to update CSM.
  //    Map - Successful PBSG used to update CSM. Suitable for Queue processing.
  Map result = null
  if (parms.newPbsg) {
    //==>Map pbsg = parms.newPbsg.findAll { k, v -> (k) } // Copy all keys
    logWarn('populateNewPbsg', [ parms.ref,
      'Rebuilding PBSG ...', "New version is ${newPbsg.version}"
    ])
    newPbsg.buttonsList.each { button ->
      ChildDevW vsw = getOrCreateVswWithToggle(device.getLabel(), button)
      newPbsg.lifo.push(button)
      if (vsw.switch == 'on') { pbsg_ActivateButton(newPbsg, button) }
      logTrace('populateNewPbsg', pbsg_StateHtml(newPbsg))
    }
    if (!newPbsg.active && newPbsg.dflt) {
      logTrace('populateNewPbsg', "Activating default ${b(newPbsg.dflt)}")
      pbsg_ActivateButton(newPbsg, newPbsg.dflt)
    }
    // Capture data for sendEvent(...) updates.
    Integer oldCnt = STATE[DID()].buttonsList.size()
    Integer newCnt = newPbsg.buttonsList.size()
    Integer oldPos = buttonNameToPushed(STATE[DID()].active, STATE[DID()].buttonsList)
    Integer newPos = buttonNameToPushed(pbsg.active, pbsg.buttonsList)
    String desc = "${STATE[DID()].active} (${oldPos}) → ${newPbsg.active} (${newPos})"
    Boolean activeChanged = STATE[DID()].active != newPbsg.active
    Boolean cntChanged = oldCount != newCount
    STATE[DID()] = newPbsg
    logTrace('populateNewPbsg', 'Release cmdQueueHandler for prior version.')
    unschedule('cmdQueueHandler') // Release Handler for Prior Version
    logTrace('populateNewPbsg', "Launch cmdQueueHandler for version ${newPbsg.version}.")
    Map args = [ version: newPbsg.version, ref: parms.ref ]
    runInMillis(500, 'cmdQueueHandler', [data: args])
    result = newPbsg
    // Update Related Attributes
    logTrace('populateNewPbsg', "Updating active: ${b(newPbsg.active)}")
    device.sendEvent(
      name: 'active',
      isStateChange: activeChanged,
      value: newPbsg.active,
      unit: '#',
      descriptionText: desc
    )
    logTrace('populateNewPbsg', "Updating numberOfButtons: ${b(newCnt)}")
    device.sendEvent(
      name: 'numberOfButtons',
      isStateChange: cntChanged,
      value: newCnt,
      unit: '#',
      descriptionText: desc
    )
    String jsonPbsg = toJson(pbsg)
    logTrace('populateNewPbsg', "Updating jsonPbsg: ${bMap(pbsg)}")
    device.sendEvent(
      name: 'jsonPbsg',
      isStateChange: true,
      value: jsonPbsg,
      descriptionText: desc
    )
    pruneOrphanedDevices(newPbsg)
    result = newPbsg
  } else {
    logError('populateNewPbsg', 'Missing parms.pbsg')
  }
  return result
}

void cmdQueueHandler(Map parms) {
  // Abstract
  //   Process arriving commands (for this version only) updating STATE
  //   for this PBSG when appropriate.
  // Input
  //   parms.version - Accept commands for this version only.
  //       parms.ref - Context string provided by caller
  while (1) {
    // At a later date, poll vs. take
    Map command = QUEUE[DID()].take()
    switch(command.name) {
      case 'Activate':
        String button = command.arg
        logTrace('activate', button)
        pbsg_ActivateButton(pbsg, button)
        ciPbsg(pbsg, command.ref)
        break
      case 'Deactive':
        String button = command.arg
        logTrace('deactivate', button)
        pbsg_DeactivateButton(pbsg, button)
        ciPbsg(pbsg, command.ref)
        break
      case 'Toggle':
        logError('cmdQueueHandler', 'TOGGLE NOT IMPLEMENTED')
        break
      default:
        logError('cmdQueueHandler', "Unexpected Command: ${command}")
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

void pruneOrphanedDevices(Map pbsg) {
  ArrayList buttonsList = STATE[DID()].buttonsList
  ArrayList expectedChildDnis = buttonsList.collect { button ->
    "${device.getLabel()}_${button}"
  }
  ArrayList currentChildDnis = getChildDevices().collect { d ->
    d.getDeviceNetworkId()
  }
  ArrayList orphanedDevices = currentChildDnis?.minus(expectedChildDnis)
  orphanedDevices.each { dni ->
    logWarn('pruneOrphanedDevices', "Removing orphaned device ${b(dni)}.")
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
    logError('push', 'null STATE[DID()].buttonsList')
  }
}

//// UNUSED / UNSUPPORTED

void parse(String) {
  // This method is reserved for interaction with FUTURE parent devices.
  logError('parse(String)', 'Called unexpectedly')
}
