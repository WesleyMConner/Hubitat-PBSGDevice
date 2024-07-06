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

String timestampAsString() { return java.time.Instant.now().toString() }

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
    version: timestampAsString()
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

// STATE ALTERING METHODS

void updatePbsgStructure(Map parms) {
  // Abstract
  //   * Evaluates the health of config (settings overlayed with parms.conf).
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
      issues << "Unrecognized config ${b('logLevel')} '${config.logLevel}', defaulting to TRACE."
      device.updateSetting('logLevel', 'TRACE')
      setLogLevel('TRACE')
    } else {
      setLogLevel(config.logLevel)
    }
    if (config.logVswActivity == null) {
      issues << "The setting ${b('logVswActivity')} is null, forcing TRUE"
      device.updateSetting('logVswActivity', true)
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
        version: timestampAsString(),
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

void populateNewPbsg(Map parms) {
  // Abstract
  //   Rebuild the PBSG per the structure in parms.newPbsg.
  //     * Updates STATE
  //     * Replace cmdQueueHandler (focused on the new Version)
  //     * Update appropriate PBSG Attributes
  // Input
  //   parms.newPbsg - Initialized PBSG Map w/ structural fields populated.
  //       parms.ref - Context string provided by caller
  Map pbsg = null
  if (parms.newPbsg) {
    pbsg = airGap(parms.newPbsg)
    logWarn('populateNewPbsg', [ parms.ref,
      'Rebuilding PBSG ...', "New version is ${pbsg.version}"
    ])
    pbsg.buttonsList.each { button ->
      ChildDevW vsw = getOrCreateVswWithToggle(device.getLabel(), button)
      pbsg.lifo.push(button)
      if (vsw.switch == 'on') { pbsgActivate(pbsg, button, parms.ref) }
    }
    if (!pbsg.active && pbsg.dflt) {
      pbsgActivate(pbsg, pbsg.dflt, parms.ref)
    }
    logTrace('populateNewPbsg', 'Handing off to savePbsgState()')
    savePbsgState(pbsg: pbsg, ref: parms.ref)
  } else {
    logError('populateNewPbsg', 'Called with null parms.newPbsg')
  }
}

void savePbsgState(Map parms) {
  // Abstract
  //   Update STATE for the PBSG and publish appropriate Attributes.
  //     * Updates STATE
  //     * Replace cmdQueueHandler (focused on the new Version)
  //     * Update appropriate PBSG Attributes
  // Input
  //   parms.pbsg - In-memory PBSG for updating STATE.
  //    parms.ref - Context string provided by caller
  if (parms.pbsg) {
    pbsg = airGap(parms.pbsg)
    // Capture data for sendEvent(...) updates BEFORE updating STATE.
    Integer oldCnt = STATE[DID()].buttonsList.size()
    Integer newCnt = pbsg.buttonsList.size()
    Integer oldPos = buttonNameToPushed(STATE[DID()].active, STATE[DID()].buttonsList)
    Integer newPos = buttonNameToPushed(pbsg.active, pbsg.buttonsList)
    String from = "${i(STATE[DID()].active)} (${i(oldPos)})"
    String to = "${b(pbsg.active)} (${b(newPos)})"
    String desc = "[Change: ${from} → ${to}]"
    Boolean activeChanged = STATE[DID()].active != pbsg.active
    Boolean cntChanged = oldCount != newCount
    STATE[DID()] = pbsg
    logTrace('populateNewPbsg', 'Replacing cmdQueueHandler for new PBSG version.')
    Map args = [ version: pbsg.version, ref: parms.ref ]
    runInMillis(500, 'cmdQueueHandler', [data: args, overwrite: true])
    logTrace('populateNewPbsg', "Updating jsonPbsg for: ${bMap(pbsg)}, (${desc})")
    device.sendEvent(
      name: 'jsonPbsg',
      isStateChange: true,
      value: toJson(pbsg),
      descriptionText: desc
    )
    if (activeChanged) {
      logTrace('populateNewPbsg', "Updating active: ${b(pbsg.active)}, (${desc})")
      device.sendEvent(
        name: 'active',
        isStateChange: activeChanged,
        value: pbsg.active,
        unit: '#',
        descriptionText: desc
      )
    }
    if (cntChanged) {
      logTrace('populateNewPbsg', "Updating numberOfButtons: ${b(newCnt)}, (${desc})")
      device.sendEvent(
        name: 'numberOfButtons',
        isStateChange: cntChanged,
        value: newCnt,
        unit: '#',
        descriptionText: desc
      )
    }
    // Update Related Attributes
    pruneOrphanedDevices(pbsg)
  } else {
    logError('populateNewPbsg', 'Missing parms.pbsg')
  }
}

void cmdQueueHandler(Map parms) {
  // Abstract
  //   This handler compares its Version with the Version cited in the Command.
  //     - If the command is older, it is considered 'stale' and skipped.
  //     - If the versions agree, the command is executed.
  //     - If the command is newer, an error is thrown. [This handler is stale.]
  // Input
  //   parms.version - Accept commands for this version only.
  //       parms.ref - Context string provided by caller
  while (1) {
    Map command = QUEUE[DID()].take()
    // Version Strings can be compared (w/out parsing them back to Instants).
    //   - Long durationMs = Duration.between(command.version, parms.version)
    //                               .toMillis()
    if (command.version == parms.version) {
      if (command.version != parms.version) {
        logWarn('cmdQueueHandler', [ "Version: ${parms.version}, dropping stale:",
          "command: ${command}"
        ])
      } else {
        switch(command.name) {
          case 'Activate':
            String button = command.arg
            Map pbsg = STATE[DID()]
            pbsgActivate(pbsg, button, command.ref)
            /*
            // ACTIVATE START
            if (pbsg?.active == button) {
              logInfo('cmdQueueHandler', ["Ignoring 'Activate ${b(button)}'",
                'The button is already active.'
              ])
            } else if (pbsg.lifo.contains(button)) {
              logInfo('cmdQueueHandler', "Activating ${b(button)}")
              if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
              pbsg.lifo.removeAll([button])
              pbsg.active = button
              savePbsgState(pbsg: pbsg, ref: command.ref)
            } else {
              logWarn('cmdQueueHandler', ["Ignoring 'Activate ${b(button)}'",
                "The button is not found. (PBSG: ${bMap(pbsg)})"
              ])
            }
            // ACTIVATE END
            */
            break
          case 'Deactivate':
            String button = command.arg
            Map pbsg = STATE[DID()]
            pbsgDeactivate(pbsg, button, command.ref)
            /*
            // DEACTIVATE START
            if (pbsg.active != button) {
              logWarn('cmdQueueHandler', ["Ignoring 'Deactivate ${b(button)}'",
                'The button is not active.'
              ])
            } else if (pbsg.active == pbsg.dflt) {
              logWarn('cmdQueueHandler', ["Ignoring 'Deactivate ${b(button)}'",
                'The button is the default button'
              ])
            } else if (pbsg.active == button) {
              logInfo('cmdQueueHandler', "Deactivating ${b(button)}")
              pbsg.lifo.push(pbsg.active)
              pbsg.active = null
              if (pbsg.dflt) {
                pbsg.lifo.removeAll([pbsg.dflt])
                pbsg.active = pbsg.dflt
              }
              savePbsgState(pbsg: pbsg, ref: command.ref)
            }
            // DEACTIVATE END
            */
            break
          case 'Toggle':
            String button = command.arg
            Map pbsg = STATE[DID()]
            if (pbsg.active == button) {
              if (button == pbsg.dflt) {
                logWarn('cmdQueueHandler', ["Ignoring 'Toggle ${b(button)}'",
                  "The button is 'on' AND is also the default button"
                ])
              } else {
                logInfo('cmdQueueHandler', "Toggling ${b(button) 'off'}")
                pbsg.lifo.push(pbsg.active)
                pbsg.active = null
                if (pbsg.dflt) {
                  pbsg.lifo.removeAll([pbsg.dflt])
                  pbsg.active = pbsg.dflt
                }
                savePbsgState(pbsg: pbsg, ref: command.ref)
              }
            } else {
              if (pbsg.lifo.contains(button)) {
                logInfo('cmdQueueHandler', "Toggling ${b(button) 'on'}")
                if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
                pbsg.lifo.removeAll([button])
                pbsg.active = button
                savePbsgState(pbsg: pbsg, ref: command.ref)
              } else {
                logWarn('cmdQueueHandler', ["Ignoring 'Toggle ${b(button)}'",
                  "The button is not found. (PBSG: ${bMap(pbsg)})"
                ])
              }
            }
            break
          default:
            logError('cmdQueueHandler', "Unexpected Command: ${command}")
        }
      }
    } else if (command.vesion < parms.version) {
      logWarn('cmdQueueHandler', ["Handler Version (${parms.version})",
        'Ignoring stale command',
        bMap(command)
      ])
    } else {
      logError('cmdQueueHandler', ["Handler Version (${parms.version})",
        'Received command that is newer than the handler.',
        bMap(command)
      ])
    }
  }
}

Map pbsgActivate(Map pbsg, String button, String ref = null) {
  if (pbsg?.active == button) {
    logInfo('cmdQueueHandler', ["Ignoring 'Activate ${b(button)}'",
      'The button is already active.'
    ])
  } else if (pbsg.lifo.contains(button)) {
    logInfo('cmdQueueHandler', "Activating ${b(button)}")
    if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
    pbsg.lifo.removeAll([button])
    pbsg.active = button
    savePbsgState(pbsg: pbsg, ref: ref)
  } else {
    logWarn('cmdQueueHandler', ["Ignoring 'Activate ${b(button)}'",
      "The button is not found. (PBSG: ${bMap(pbsg)})"
    ])
  }
  return pbsg
}

Map pbsgDeactivate(Map pbsg, String button, String ref) {
  if (pbsg.active != button) {
    logWarn('cmdQueueHandler', ["Ignoring 'Deactivate ${b(button)}'",
      'The button is not active.'
    ])
  } else if (pbsg.active == pbsg.dflt) {
    logWarn('cmdQueueHandler', ["Ignoring 'Deactivate ${b(button)}'",
      'The button is the default button'
    ])
  } else if (pbsg.active == button) {
    logInfo('cmdQueueHandler', "Deactivating ${b(button)}")
    pbsg.lifo.push(pbsg.active)
    pbsg.active = null
    if (pbsg.dflt) {
      pbsg.lifo.removeAll([pbsg.dflt])
      pbsg.active = pbsg.dflt
    }
    savePbsgState(pbsg: pbsg, ref: ref)
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
    table << "<td>${devHued(device)}:&nbsp;</td>"
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

// SUPPORT FOR CAPABILITY 'PushableButton'

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

// UNUSED / UNSUPPORTED

void parse(String) {
  // This method is reserved for interaction with FUTURE parent devices.
  logError('parse(String)', 'Called unexpectedly')
}
