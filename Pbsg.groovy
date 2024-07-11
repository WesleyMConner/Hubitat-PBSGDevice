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
    command 'configPbsg', [
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
    command 'simulateVswOn', [
      [ name:'button', type: 'text', description: 'TEST vsw.on()' ],
      [ name: 'description', type: 'text', description: 'optional text' ]
    ]
    command 'simulateVswOff', [
      [ name:'button', type: 'text', description: 'TEST vsw.off()' ],
      [ name: 'description', type: 'text', description: 'optional text' ]
    ]
    command 'simulateVswPush', [
      [ name:'buttonNumber', type: 'Integer', description: 'TEST vsw.push()' ] //,
      //[ name: 'description', type: 'text', description: 'optional text' ]
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
  }
}

// System Device Management Methods

void installed() {
  // Called when a bare device is first constructed.
  logWarn('installed', 'Initializing STATE and QUEUE PBSG entries.')
  STATE[DID()] = getEmptyPbsg()
  QUEUE[DID()] = new SynchronousQueue<Map>(true) // TRUE → FIFO
  logInfo('installed', 'Launching Command Queue Handler.')
  runInMillis(100, 'commandProcessor', [data: []])
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
  STATE[DID()] = getEmptyPbsg()
  QUEUE[DID()] = new SynchronousQueue<Map>(true) // TRUE → FIFO
  runInMillis(100, 'commandProcessor', [:])
}

void updated() {
  // Called when a human uses the Hubitat GUI's Device drilldown page to edit
  // preferences (aka settings) AND presses 'Save Preferences'.
  logTrace('updated', 'Attempting PBSG Rebuild from configuration data.')
  updatePbsgStructure(parms: [ref: 'Invoked by updated()'])
}

// Utility Methods

String timestampAsString() { return java.time.Instant.now().toString() }

Integer buttonNameToPushed(String button, ArrayList buttons) {
  // Button name to button 'keypad' position is always computed 'on-the-fly'.
  return buttons?.withIndex().collectEntries { b, i ->
    [(b), i+1]
  }?."${button}"
}

// Externally-Exposed PBSG Commands

void configPbsg(String jsonPrefs, String ref = '') {
  // If the configuration change alters the PBSG structure:
  //   - Rebuild the PBSG to the new structure
  //   - Update the PBSG version.
  updatePbsgStructure(config: jsonPrefs, ref: 'Invoked by configpbsg()')
}

void push(Integer buttonNumber, String ref = '') {
  // This method supports Capability 'PushableButton'.
  if (buttonNumber) {
    Map command = [
      name: 'Push',
      arg: buttonNumber,
      ref: ref,
      version: STATE[DID()].version
    ]
    logTrace('push', "queueing ${bMap(command)}")
    enqueueCommand(command)
  } else {
    logError('push', 'Called with buttonNumber=NULL')
  }
}

void activate(String button, String ref = '') {
  if (button) {
    Map command = [
      name: 'Activate',
      arg: button,
      ref: ref,
      version: STATE[DID()].version
    ]
    logTrace('activate', "queueing: ${bMap(command)}")
    enqueueCommand(command)
  } else {
    logError('activate', 'Called with button=NULL')
  }
}

void deactivate(String button, String ref = '') {
  if (button) {
    Map command = [
      name: 'Deactivate',
      arg: button,
      ref: ref,
      version: STATE[DID()].version
    ]
    logTrace('deactivate', "queueing ${bMap(command)}")
    enqueueCommand(command)
  } else {
    logError('deactivate', 'Called with button=NULL')
  }
}

// Internal Methods

void updatePbsgStructure(Map parms) {
  // Abstract
  //   * Evaluates the health of configuration (i.e., settings overlayed with
  //     parms.conf).
  //   * If config is healthy and the PBSG STRUCTURE has changed, rebuild PBSG.
  // Input
  //   parms.config - Map of <k, v> pairs that overwrite settings <k, v> pairs.
  //      parms.ref - Context string provided by caller
  // Output
  //   null - Config is unhealthy or unchanged relative to CSM, see logs.
  //    Map - The new PBSG (as saved to STATE).
  Map config = settings              // Insert Preference <k, v> pairs.
  config << parseJson(parms.config)  // Overlay provided <k, v> pairs.
  ArrayList issues = []
  ArrayList buttonsList = []
  if (config) {
    // Process the (two) log fields which ARE NOT part of STATE.
    if (config.logLevel == null) {
      issues << "Missing config ${b('logLevel')}, defaulting to TRACE."
      device.updateSetting('logLevel', 'TRACE')
      setLogLevel('TRACE')
    } else if (!['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].contains(config.logLevel)) {
      issues << [
        "Unrecognized config ${b('logLevel')} '${config.logLevel}', ",
        'defaulting to TRACE.'
      ].join()
      device.updateSetting('logLevel', 'TRACE')
      setLogLevel('TRACE')
    } else {
      setLogLevel(config.logLevel)
    }
    // Reviewing PBSG Structural fields.
    Boolean healthyButtons = true
    String markDirty = config?.buttons?.replaceAll(/[\W_&&[^_ ]]/, '▮')
    buttonsList = config?.buttons?.tokenize(' ')
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
    if (healthyButtons) {
      device.updateSetting('buttons', buttonsList.join(' '))
    }
    if (config.dflt == null) {
      issues << "The setting ${b('dflt')} is null (expected 'not_applicable')"
    }
    if (! [buttonsList.contains(config.dflt)]) {
      issues << [
        "The setting ${b('dflt')} (${config.dflt}) is not found among ",
        "buttons: ${bList(buttonsList)}"
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
    logError('updatePbsgStructure', [ i(parms.ref),
      'The following issues prevent a PBSG (re-)build at this time',
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
      // Populate newPbsg with Structural fields only.
      Map newPbsg = [
        version: timestampAsString(),
        buttonsList: buttonsList,
        dflt: dflt,
        instType: 'pbsg',
        active: null,
        lifo: []
      ]
      logWarn('updatePbsgStructure', [ i(parms.ref),
        "The PBSG structure has changed, new version: ${b(newPbsg.version)}."
      ])
      Map pbsg = rebuildPbsg(newPbsg: newPbsg, ref: parms.ref)
    } else {
      logWarn('updatePbsgStructure', [ i(parms.ref),
        'The PBSG is healthy and DOES NOT REQUIRE a rebuild.'
      ])
    }
  }
}

void rebuildPbsg(Map parms) {
  // Abstract
  //   Rebuild the PBSG per the structure in parms.newPbsg.
  //     * Updates STATE
  //     * Update appropriate PBSG Attributes
  // Input
  //       parms.ref - Context string provided by caller
  if (parms.newPbsg) {
    Map pbsg = airGap(parms.newPbsg)
    pbsg.buttonsList.each { button ->
      ChildDevW vsw = getOrCreateVswWithToggle(
        device.getLabel(),
        button,
        buttonNameToPushed(button, pbsg.buttonsList)
      )
      pbsg.lifo.push(button)
      if (vsw.switch == 'on') {
        pbsg_Activate(pbsg, button, parms.ref)
      }
    }
    if (!pbsg.active && pbsg.dflt) {
      pbsg_Activate(pbsg, pbsg.dflt, parms.ref)
    }
    pbsg_SaveState(pbsg: pbsg, ref: parms.ref)
  } else {
    logError('rebuildPbsg', 'Called with null parms.newPbsg')
  }
}

void pbsg_SaveState(Map parms) {
  // Abstract
  //   Update STATE for the PBSG and publish appropriate Attributes.
  //     * Updates STATE
  //     * Update appropriate PBSG Attributes
  // Input
  //   parms.pbsg - In-memory PBSG for updating STATE.
  //    parms.ref - Context string provided by caller
  if (parms.pbsg) {
    String ref = parms.ref ? i(" Ref: ${parms.ref}") : ''
    pbsg = airGap(parms.pbsg)
    // Capture data for sendEvent(...) updates BEFORE updating STATE.
    Integer oldCnt = STATE[DID()].buttonsList.size()
    Integer newCnt = pbsg.buttonsList.size()
    Integer oldPos = buttonNameToPushed(STATE[DID()].active, STATE[DID()].buttonsList)
    Integer newPos = buttonNameToPushed(pbsg.active, pbsg.buttonsList)
    String from = "${i(STATE[DID()].active)} (${i(oldPos)})"
    String to = "${b(pbsg.active)} (${b(newPos)})"
    String desc = "[${from} → ${to}]"
    Boolean activeChanged = (STATE[DID()].active != pbsg.active)
    Boolean cntChanged = (oldCnt != newCnt)
    STATE[DID()] = pbsg
    logTrace('pbsg_SaveState', "pbsg ${bMap(pbsg)}")
    // Reconcile VSWs
    pbsg.buttonsList.each{ button ->
      updateVswState(button, (pbsg.active == button) ? 'on' : 'off')
    }
    logTrace('pbsg_SaveState', ['Updating jsonPbsg',
      bMap(pbsg),
      pbsg_StateHtml(pbsg)
    ])
    device.sendEvent(
      name: 'jsonPbsg',
      isStateChange: true,
      value: toJson(pbsg),
      descriptionText: ref
    )
    if (activeChanged) {
      logTrace('pbsg_SaveState', "Updating active: ${b(pbsg.active)}, (${desc})")
      device.sendEvent(
        name: 'active',
        isStateChange: activeChanged,
        value: pbsg.active,
        unit: '#',
        descriptionText: desc + ref
      )
    }
    if (cntChanged) {
      logTrace('pbsg_SaveState', "Updating numberOfButtons: ${b(newCnt)}, (${desc})")
      device.sendEvent(
        name: 'numberOfButtons',
        isStateChange: cntChanged,
        value: newCnt,
        unit: '#',
        descriptionText: desc + ref
      )
    }
    // Update Related Attributes
    pruneOrphanedDevices(pbsg)
  } else {
    logError('pbsg_SaveState', 'Missing parms.pbsg')
  }
}

Map getEmptyPbsg() {
  return [
    version: timestampAsString(),
    buttonsList: [],
    dflt: null,
    instType: 'pbsg',
    active: null,
    lifo: []
  ]
}

void enqueueCommand(Map command) {
  QUEUE[DID()].put(command)
}

void commandProcessor(Map parms) {
  // Enter a loop to consume commands issued via QUEUE[DID()].
  //   - If the versions agree, the command is executed.
  //   - If the command is older, it is considered 'stale' and dropped.
  //   - If the command is newer, an error is thrown.
  logWarn('commandProcessor', 'QUEUE HANDLER LOOP STARTED')
  //-> logInfo('commandProcessor', "parms: ${parms}")
  //-> pauseExecution(1000)
  while (1) {
    logInfo('commandProcessor', 'Awaiting next take().')
    Map command = QUEUE[DID()].take()
    Map pbsg = STATE[DID()]
    logInfo('commandProcessor', ['Processing command:',
      bMap(command)
    ])
    if (pbsg.version == command.version) {
      switch(command.name) {
        case 'Activate':
          String button = command.arg
          logTrace('commandProcessor', "case Activate for ${b(button)}.")
          pbsg_Activate(pbsg, button, command.ref)
          pbsg_SaveState(pbsg: pbsg, ref: command.ref)
          break
        case 'Deactivate':
          String button = command.arg
          logTrace('commandProcessor', "case Deactivate for ${b(button)}.")
          pbsg_Deactivate(pbsg, button, command.ref)
          pbsg_SaveState(pbsg: pbsg, ref: command.ref)
          break
        case 'Push':
          Integer buttonNumber = command.arg
          String button = pbsg.buttonsList[buttonNumber]
          logTrace('commandProcessor', "case Toggle for ${b(button)}.")
          if (pbsg.active == button) {
            if (button == pbsg.dflt) {
              logWarn('commandProcessor', ["Ignoring 'Toggle ${b(button)}'",
                "The button is 'on' AND is also the default button"
              ])
            } else {
              logInfo('commandProcessor', "Toggling ${b(button)} 'off'")
              pbsg.lifo.push(pbsg.active)
              pbsg.active = null
              if (pbsg.dflt) {
                pbsg.lifo.removeAll([pbsg.dflt])
                pbsg.active = pbsg.dflt
              }
              pbsg_SaveState(pbsg: pbsg, ref: command.ref)
            }
          } else {
            if (pbsg.lifo.contains(button)) {
              logInfo('commandProcessor', "Toggling ${b(button)} 'on'")
              if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
              pbsg.lifo.removeAll([button])
              pbsg.active = button
              pbsg_SaveState(pbsg: pbsg, ref: command.ref)
            } else {
              logWarn('commandProcessor', ["Ignoring 'Toggle ${b(button)}'",
                "The button is not found. (PBSG: ${bMap(pbsg)})"
              ])
            }
          }
          break
        default:
          logError('commandProcessor', "Unknown Command: ${command}")
      }
    } else if (command.version < pbsg.version) {
      logWarn('commandProcessor', ['Dropping stale command.',
        "command.version: ${b(command.version)}",
        "   pbsg.version: ${b(pbsg.version)}"
      ])
    } else {
      logError('commandProcessor', ['PBSG is stale?!',
        "command.version: ${b(command.version)}",
        "   pbsg.version: ${b(pbsg.version)}"
      ])
    }
  }
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

String pbsg_StateText(Map pbsg) {
  // IMPORTANT:
  //   LIFO push() and pop() are supported, *BUT* pushed items are appended
  //   (not prepended). See "reverse()" below, which compensates.
  String result
  if (pbsg) {
    ArrayList list = ["${devHued(device)}: "]
    list << (pbsg.active ? buttonState(pbsg.active) : 'null')
    list << ' ← '
    pbsg.lifo?.reverse().each { button ->
      if (button) { list << buttonState(button) }
    }
    list << "<b>LIFO</b>"
    result = list.join()
  } else {
    result = "${devHued(device)}: null"
  }
  return result
}

String pbsg_StateHtml(Map pbsg) {
  // IMPORTANT:
  //   LIFO push() and pop() are supported, *BUT* pushed items are appended
  //   (not prepended). See "reverse()" below, which compensates.
  ArrayList table = []
  if (pbsg) {
    table << '<span style="display:inline-table;">'
    table << '<table><tr>'
    //table << "<td>${devHued(device)}:&nbsp;</td>"
    //->table << "<td>${XXX}:&nbsp;</td>"
    table << tdBordered( pbsg.active ? buttonState(pbsg.active) : 'null' )
    table << '<td>&nbsp;←&nbsp;</td>'
    pbsg.lifo?.reverse().each { button ->
    //pbsg.lifo?.each { button ->
      table << tdBordered( buttonState(button) )
    }
    table << "<td><b>LIFO</b></td></tr></table></span>"
  }
  return table ? table.join() : null
}

// Adjust In-Memory PBSG (without altering state)

void pbsg_Activate(Map pbsg, String button, String ref = null) {
  if (pbsg?.active == button) {
    logInfo(
      'pbsg_Activate',
      "Ignoring 'Activate ${b(button)}'. The button is already active."
    )
  } else if (pbsg.lifo.contains(button)) {
    logInfo('pbsg_Activate', "Activating ${b(button)}")
    if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
    pbsg.lifo.removeAll([button])
    pbsg.active = button
  } else {
    logWarn('pbsg_Activate', ["Ignoring 'Activate ${b(button)}'",
      "The button is not found. (PBSG: ${bMap(pbsg)})"
    ])
  }
}

void pbsg_Deactivate(Map pbsg, String button, String ref) {
  if (pbsg.active != button) {
    logWarn(
      'pbsg_Deactivate',
      "Ignoring 'Deactivate ${b(button)}'. The button is not active."
    )
  } else if (pbsg.active == pbsg.dflt) {
    logWarn(
      'pbsg_Deactivate',
      "Ignoring 'Deactivate ${b(button)}. The button is the default button."
    )
  } else if (pbsg.active == button) {
    logInfo('pbsg_Deactivate', "Deactivating ${b(button)}")
    pbsg.lifo.push(pbsg.active)
    pbsg.active = null
    if (pbsg.dflt) {
      pbsg.lifo.removeAll([pbsg.dflt])
      pbsg.active = pbsg.dflt
    }
  }
}

// Manage VSWs and Implement VSW Methods

ChildDevW getOrCreateVswWithToggle(
  String pbsgName,
  String buttonName,
  Integer buttonPosition
) {
  // Device Network Identifier (DNI) does not include white space.
  // Device Names / Labels limit special characters to '_'.
  String dni = "${pbsgName}_${buttonName}"
  String deviveName = dni.replaceAll('_', ' ')
  ChildDevW d = getChildDevice(dni)
  if (!d) {
    d = addChildDevice(
      'WesMC',               // Device namespace
      'VswWithToggle',       // Device type
      dni,
      [
        isComponent: true,   // Lifecycle is tied to parent
        name: deviveName,
        label: deviveName
      ]
    )
    logWarn(
      'getOrCreateVswWithToggle',
      "Created new VswWithToggle instance: ${devHued(d)}"
    )
  }
  d.setButtonNameAndPosition(buttonName, buttonPosition)
  return d
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

void updateVswState(String button, String value) {
  if (button) {
    if (value == 'on' || value == 'off') {
      ChildDevW d = getVswForButton(button)
      if (d) {
        // Create the ArrayList of commands (Maps) for
        ArrayList commands = [] << [
          name: 'switch',
          value: value,
          descriptionText: "Turned on ${d.getDeviceNetworkId()}",
          isStateChange: (d.switch != value)
        ]
        d.parse(commands)
        logInfo(d.getDeviceNetworkId(), 'Turned on')
      } else {
        logError('updateVswState', "Failed to get VSW for ${button}")
      }
    } else {
      logError('updateVswState', "Expected value 'on' or 'off', got '${value}'.")
    }
  } else {
    logError('updateVswState', 'Received null parameter "button"')
  }
}

// VSW SUPPORT

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

// UNUSED / UNSUPPORTED

void parse(String) {
  // This method is reserved for interaction with FUTURE parent devices.
  logError('parse(String)', 'Called unexpectedly')
}
