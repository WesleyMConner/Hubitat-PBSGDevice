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
import java.text.SimpleDateFormat
//import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
//import java.util.concurrent.SynchronousQueue

@Field static ConcurrentHashMap<Long, Map> perPbsgState = [:]
//@Field static ConcurrentHashMap<Long, String> perPbsgVersion = [:]
//@Field static ConcurrentHashMap<Long, SynchronousQueue<Map>> perPbsgQueue = [:]
@Field static ConcurrentHashMap<Long, ArrayList> perPbsgQueue = [:]

Map csm() {  // Get the PBSG's Concurrent State Map (csm)
  return perPbsgState[device.idAsLong]
}

//String currentVersion() {
//  return perPbsgVersion[device.idAsLong]
//}

// Prefer: java.time.Instant.now()
//-> String newVersion() {
//->   SimpleDateFormat df = new SimpleDateFormat("YYYY-MM-DD-HH:mm:ss");
//->   return df.format(new Date())
//-> }

ArrayList cmdQueue() {
  return perPbsgQueue[device.idAsLong]
}

/*
Integer nextVersion() {
  Integer version = 1
  if (!perPbsgVersion[device.idAsLong]) {           // Initialize
    perPbsgVersion[device.idAsLong] = version
  } else {
    version = (version < Integer.MAX_VALUE)
      ?  version + perPbsgVersion[device.idAsLong]  // Increment
      : 1                                           // Rollover
    perPbsgVersion[device.idAsLong] = rqstId
  }
  return rqstId
}
*/

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
    //command 'buttonNameToPushed', [
    //  [ name: 'button', type: 'text', description: 'position 1..N of button']
    //]
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

void installed() {
  // Called when a bare device is first constructed.
  perPbsgState[device.idAsLong] = [
    // NOTE: Update this whole Map atomically.
    // STRUCTURAL FIELDS
    version: java.time.Instant.now(),  // Version Timestamp .... String
    buttonsList: [],                   // All PBSG buttons ..... ArrayList
    dflt: null,                        // Default button ....... String | null
    instType: 'pbsg',                  // PBSG 'psuedo-class' .. String
    // DYNAMIC FIELDS
    active: null,                      // Active button ........ String | null
    lifo: []                           // Inactive buttons ..... ArrayList
  ]
  logInfo('installed', "perPbsgState: ${perPbsgState}")
  perPbsgQueue[device.idAsLong] = [] //new SynchronousQueue(true)
  logWarn('installed', [
    "Constructed ${devHued(device)}.",
    "Initial Concurrent State Map (csm): ${bMap(csm())}."
  ])
  // Tactically let's try one take().
  //logInfo('installed', 'BEFORE TAKE')
  //Map cmd = cmdQueue().take()
  //logInfo('installed', "AFTER TAKE, cmd: ${cmd}")
}

void uninstalled() {
  // Called on device tear down.
  logWarn(
    'uninstalled',
    'Removing perPbsgState and perPbsgQueue entries.'
  )
  perPbsgState.remove(device.idAsLong)
  //perPbsgVersion.remove(device.idAsLong)
  perPbsgQueue.remove(device.idAsLong)
}

void initialize() {
  // Called on hub startup (per capability "Initialize").
  // Rebuilding/repopulating the Concurrent State Map (csm) for this device.
  logTrace('initialize', 'Rebuilding PBSG if settings are sufficient.')
  Map newStructure = newPbsg(ref: 'initialize()')
  if (newStructure) {
    Map pbsg = rebuildPbsg(newStructure)
    if (pbsg) {
      Map command = pbsg.take() //poll(2, SECONDS)
      if (command) {
        logInfo('initialize', "command: ${command}")
      }
    }
  }
}

void updated() {
  // Called when a human uses the Hubitat GUI's Device drilldown page to edit
  // preferences (aka settings) AND presses 'Save Preferences'.
  //   - If (and only if) devices settings have changed, the PBSG is rebuilt.
  //   - On PBSG rebuild, anything pending in the commandQueue is dropped.
  logTrace('updated', 'Rebuilding PBSG if settings are sufficient.')
  Map newPbsg = newPbsg(ref: 'updated()')
  if (newPbsg) {
    Map pbsg = rebuildPbsg(newPbsg: newPbsg)
    if (pbsg) {
      Map command = pbsg.poll(2, SECONDS)
      if (command) { logInfo('updated', "command: ${command}") }
    } else {
      logError('updated', 'rebuildPbsg() failed, returning null')
    }
  }
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
  Map pbsg = csm()
  //-> logTrace('config', "versionA: ${pbsg.version}")       // SUCCESS
  //-> logTrace('config', "versionB: ${pbsg[version]}")      // FAIL
  //-> logTrace('config', "versionC: ${pbsg."${version}"}")  // FAIL
  //logTrace('config', ['',
  //  "Queueing Config w/ jsonPrefs: ${jsonPrefs}",
  //  "ref: ${ref}",
  //  "perPbsgState[device.idAsLong]: ${perPbsgState[device.idAsLong]}",
  //  "csm(): ${csm()}"
  //])
  //-> Map cmd =
  //-> logTrace('config', "cmd: ${bMap(cmd)}")
  //SynchronousQueue<Map> q = cmdQueue()
  //-> logTrace('config', "TEST: ${q != null}")
  //boolean result = q.offer(cmd, 2, SECONDS)
  logTrace('config', "BEFORE queue add, q: ${cmdQueue()}")
  cmdQueue() << [
    name: 'Config',
    arg: jsonPrefs,
    ref: ref,
    version: pbsg.version
  ]
  logTrace('config', "AFTER queue add, q: ${cmdQueue()}")
  Map cmd = cmdQueue().pop()
  logTrace('config', "AFTER queue pop, cmd: ${cmd}")
}

void activate(String button, String ref = '') {
  logTrace('activate', "Queueing Activate ${b(button)}.")
  cmdQueue().put([
    name: 'Activate',
    arg: button,
    ref: ref,
    version: csm()?."${version}"
  ]) //, 2, SECONDS)
}

void deactivate(String button, String ref = '') {
  logTrace('deactivate', "Queueing Deactivate ${b(button)}.")
  //cmdQueue().offer([
  cmdQueue().put([
    name: 'Deactivate',
    arg: button,
    ref: ref,
    version: csm().version
  ]) //, 2, SECONDS)
}

void toggle(String button, String ref = '') {
  logTrace('toggle', "Queueing Toggle ${b(button)}.")
  //cmdQueue().offer([
  cmdQueue().put([
    name: 'Toggle',
    arg: button,
    ref: ref,
    version: csm().version
  ]) //, 2, SECONDS)
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

Map newPbsg(Map parms) {
  // Abstract
  //   Determine if the STATIC component of the PBSG Concurrent State Map (CSM)
  //   has changed - necessitating a Version update. If the structure of the
  //   PBSG has changed, build/rebuild the DYNAMIC component of the PBSG and
  //   update the CSM.
  // Input
  //   parms.config - Map of <k, v> pairs that overwrite settings <k, v> pairs.
  //      parms.ref - Context string provided by caller
  // Output
  //   null - Config is unhealthy or unchanged relative to CSM, see logs.
  //    Map - newPbsg wtih only the structural <k, v> pairs populated.
  Map newPbsg = null
  Map config = settings   // Insert Preference <k, v> pairs.
  config << parms.config  // Prefer any <k, v> pairs supplied via parms.config.
  ArrayList issues = []   // Keep track of net config issues.
  if (config) {
    // Two log fields ARE NOT part of CSM.
    if (config.logLevel == null) {
      issues << "The setting ${b('logLevel')} is null."
    } else {
      setLogLevel(config.logLevel ?: 'TRACE')  // Copious logging
    }
    if (config.logVswActivity == null) {
      issues << "The setting ${b('logVswActivity')} is null."
    }
    // Proceed to STATIC CSM fields.
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
    // Normalize settings.buttons.
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
    // Report the discovered issues and stop.
    logError('newPbsg', [ parms.ref,
      'The following known issues prevent a PBSG (re-)build at this time',
      *issues
    ])
  } else {
    // Does the (healthy) configuration merits a PBSG rebuild?
    String dflt = (config.dflt == 'not_applicable') ? null : config.dflt
    Boolean structureAltered = (
      config.buttonsList != csm().buttonsList
      || dflt != csm().dflt
      || config.instType != csm().instType
    )
    if (structureAltered) {
      logWarn('newPbsg', [ parms.ref,
        'The PBSG structure has changed, necessitating a PBSG rebuild.'
      ])
      // Initialize the prospective Concurrent State Map (CSM) update in memory.
      newPbsg = [
        // STRUCTURAL FIELDS
        version: java.time.Instant.now(),
        buttonsList: buttonsList,
        dflt: dflt,
        instType: config.instType,
        // DYNAMIC FIELDS
        active: null,
        lifo: []
      ]
    } else {
      logWarn('newPbsg', [ parms.ref,
        'The PBSG is healthy and DOES NOT REQUIRE a rebuild'
      ])
    }
  }
  return result
}

Map rebuildPbsg(Map parms) {
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
  if (parms?.newPbsg) {
    logWarn('rebuildPbsg', [ parms.ref,
      'Rebuilding PBSG ...', "New version is ${newPbsg.version}"
    ])
    newPbsg.buttonsList.each { button ->
      ChildDevW vsw = getOrCreateVswWithToggle(device.getLabel(), button)
      newPbsg.lifo.push(button)
      if (vsw.switch == 'on') { pbsg_ActivateButton(newPbsg, button) }
      logTrace('rebuildPbsg', pbsg_StateHtml(newPbsg))
    }
    if (!newPbsg.active && newPbsg.dflt) {
      logTrace('rebuildPbsg', "Activating default ${b(newPbsg.dflt)}")
      result = pbsg_ActivateButton(newPbsg, newPbsg.dflt)
    }
    // Capture data for sendEvent(...) updates.
    Integer oldCnt = csm().buttonsList.size()
    Integer newCnt = newPbsg.buttonsList.size()
    Integer oldPos = buttonNameToPushed(csm().active, csm().buttonsList)
    Integer newPos = buttonNameToPushed(pbsg.active, pbsg.buttonsList)
    String desc = "${csm().active} (${oldPos}) → ${newPbsg.active} (${newPos})"
    Boolean activeChanged = csm().active != newPbsg.active
    Boolean cntChanged = oldCount != newCount
    // Update CSM
    perPbsgState[device.idAsLong] = newPbsg
    logTrace('rebuildPbsg', "New CSM: ${bMap(csm())}")
    // Update Attributes
    logTrace('rebuildPbsg', "Updating active: ${b(newPbsg.active)}")
    device.sendEvent(
      name: 'active',
      isStateChange: activeChanged,
      value: newPbsg.active,
      unit: '#',
      descriptionText: desc
    )
    logTrace('rebuildPbsg', "Updating numberOfButtons: ${b(newCnt)}")
    device.sendEvent(
      name: 'numberOfButtons',
      isStateChange: cntChanged,
      value: newCnt,
      unit: '#',
      descriptionText: desc
    )
    String jsonPbsg = toJson(pbsg)
    logTrace('rebuildPbsg', "Updating jsonPbsg: ${bMap(pbsg)}")
    device.sendEvent(
      name: 'jsonPbsg',
      isStateChange: true,
      value: jsonPbsg,
      descriptionText: desc
    )
    pruneOrphanedDevices(newPbsg)
    result = newPbsg
  } else {
    logError('rebuildPbsg', 'Missing parms.pbsg')
  }
  return result
}

void processCommandQueue(Map pbsg) {
  logTrace('processCommandQueue', "pbsg: ${pbsg}")
  //-> csm().remove('activeCO')
  if (pbsg == null) {
    logError('processCommandQueue', 'Called with null "pbsg" parameter.')
  } else {
    Map command = null
    while (command = nextCommandFromQueue()) {
      switch(command.name) {
        case 'Config':
          newPbsg(pbsg, command.ref)
          break
        case 'Parse':
          //Map jsonPrefs = fromJson(command.arg, String ref = command.ref)
          Map jsonPrefs = parseJson(command.arg, ref = command.ref)
          newPbsg(pbsg, command.ref, settings << jsonPrefs)
          break
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
        /*
        case 'TestVswOn':
          break
        case 'TestVswOff':
          break
        case 'TestVswPush':
          String button = command.arg
          if (pbsg.active == button) {
            logTrace('processCommandQueue', "Push toggling ${button} off.")
            pbsg_DeactivateButton(pbsg, button)
          } else {
            logTrace('processCommandQueue', "Push toggling ${button} on.")
            pbsg_ActivateButton(pbsg, button)
          }
          break
        */
        default:
          logError('processCommandQueue', "Unexpected Command: ${command}")
      }
    }
    releaseLock()
  }
}

//-> void refreshVsws() {
//->   // Support child VSW refresh in isolation for system restart, etc.
//-> }

//----

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
  ArrayList buttonsList = csm().buttonsList
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
    logError('push', 'null csm().buttonsList')
  }
}

//// UNUSED / UNSUPPORTED

void parse(String) {
  // This method is reserved for interaction with FUTURE parent devices.
  logError('parse(String)', 'Called unexpectedly')
}
