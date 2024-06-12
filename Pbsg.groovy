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
#include wesmc.lPBSG
import groovy.json.JsonOutput    // Appears in wesmc.lPBSG
import groovy.json.JsonSlurper   // Appears in wesmc.lPBSG

// OVERVIEW
//   Conceptually, a PBSG has:
//     buttons - An ArrayList of all the buttons managed by the PBSG
//      active - The button that is currently 'on'
//               (can be null if there is no dflt button)
//        dflt - The button that is turned 'on' if active becomes null
//               (can be 'not_applicable')
//        lifo - The list of 'off' buttons where the most recently
//               turned off button is the "last in" - facilitating
//               the "activateLastActive" command.
//
//   Subscribable Events
//     PBSG state (issued for any state change):
//       [
//         == MINIMAL/COMPREHENSIVE SUMMARY OF STATE
//                     active: String or null  - 'on' button
//                       lifo: ArrayList       - 'off' buttons
//                       dflt: String or null  - 'dflt' button
//         == FOR `PushableButton` CAPABILITY
//            numberOfButtons: Integer         - N
//                     pushed: Integer         - Position 1..N
//         == FOR CONVENIENCE
//                    buttons: ArrayList       - 'all' buttons
//           buttonToPosition: Map             - button name → 1..N
//                    display: String          - Log-friendly state
//       ]
//     PushableButton (issued when cited fields change):
//       [
//            numberOfButtons: Integer         - N
//                     pushed: Integer         - Position 1..N
//       ]
//
//   VERY IMPORTANT
//     The ArrayList implementation of "lifo" is inverted !!!
//       - push() is expected to PREPEND an item which pop() retrieves
//       - Instead push() APPENDS an item which pop() retrieves
//     The 'display' String provides a visual summary active & lifo

metadata {
  definition(
    name: 'PBSG',
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    importUrl: 'PENDING',
    singleThreaded: 'true'
  ) {
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
    //=> attribute <name>, <type>
  }
  preferences {
    // There are two facilities for updating these preferences:
    //   (1) MANUALLY: Adjust via the Hubitat GUI device drilldown page
    //       pressing "Save Preferences" to register changes.
    //   (2) PROGRAMMATICALLY: Call the PBSG's parse(String json) method
    //       to register changes.
    // When changes are registered, the PBSG will (as required) adjust
    // child VSWs, adjust PBSG and VSW state, call sendEvent() to update
    // PBSG event subscribers.
    input(
      name: 'buttons',                 // settings.buttons
      title: "${b('Button Names')} (space delimited)",
      type: 'text',
      required: true
    )
    input(
      name: 'dflt',                    // settings.dflt
      title: b('Default Button'),
      type: 'enum',
      multiple: false,
      options: (settings?.buttons?.tokenize(' ') ?: []) << 'not_applicable',
      defaultValue: 'not_applicable',
      required: true
    )
    input(
      name: 'instType',                // settings.instType
      title: b('Type of PBSG'),
      type: 'text',
      defaultValue: 'pbsg',
      required: true
    )
    input(
      name: 'logLevel',                // settings.logLevel
      title: b('Logging Threshold ≥'),
      type: 'enum',
      multiple: false,
      options: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],
      defaultValue: 'INFO',
      required: true
    )
  }
}

// Per https://docs2.hubitat.com/en/developer/driver/overview,
// device data can be managed using state and/or atomicState

String showSettings() {
  return ['SETTINGS:',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join(', ')
  ].join(', ')
}

void installed() {
  // Called when device is first added; but, before sufficient settings
  // exist to configure() the device and start operations.
  logInfo('installed', "Taking no action, ${showSettings()}")
}

Integer countNumberOfButtons() {
  return settings?.buttons?.tokenize(' ')?.size() ?: 0
}

void updated() {
  // Called when 'Save Preferences' is pushed on the Device drilldown page.
  // This is one mechanism for adjusting settings (i.e., as an alternative
  // to parse(String text). Any time settings are adjusted, configure()
  // needs to be (re-)run.
  configure()
}

void parse(String jsonConfig) {
  // Exposed externally
  // Note:
  //   - Generally speaking, Hubitat devices can leverage this method to
  //     handle raw incoming data from Zigbee, Z-Wave and LAN devices,
  //     creating events as needed. Virtual devices generally do not
  //     demonstrate this behavior, as there is no data coming in from
  //     a real device.
  //   - This method is hijacked to facilitate injecting configuration
  //     data (i.e., adjusting settings) as an alternative to manually
  //     populating "Preferences" on the device's drilldown page.
  //   - The expected jsonConfig is a Map with four optional keys:
  //       toJson([
  //         instType: <String>,     // Adjusts Parameter 'instType'
  //          buttons: <ArrayList>,  // Adjusts Parameter 'buttons'
  //             dflt: <String>,     // Adjusts Parameter 'dflt'
  //         logLevel: <enum>        // Adjusts Parameter 'logLevel'
  //       ])
  logInfo('parse', "Processing JSON: '${jsonConfig}'")
  Map parms = fromJson(jsonConfig)
  if (parms.buttons) {
    logInfo('parse', "parms.buttons: ${parms.buttons}")
    settings.buttons = parms.buttons.join(' ')
  }
  if (parms.dflt) { settings.dflt = parms.dflt }
  if (parms.instType) { settings.instType = parms.instType }
  if (parms.logLevel) { settings.logLevel = parms.logLevel }
  // (Re-populate buttons
  logInfo('parse', "At exit, ${showSettings()}")
  configure()
}

void parse(ArrayList actions) {
  // This implementation is for component devices and their parents.
  // It may be developed if PBSG is specialized (e.g., RoomsPbsg).
  logWarn('parse', ['parse(ArrayList) ignored:',
    actions.join('<br/>')
  ].join('<br/>'))
}

void configure() {
  // Update changes to 'settings' (if any):
  //   - Ensure the overall consistency of settings
  //   - Adjust 'state' as required.
  //   - Reconcile the PBSG instance to its child devices.
  // NOTES
  //   - device.removeSetting('<key>')
  logInfo('configure#A', "At entry, ${showSettings()}")
  if (settings.logLevel) {
    logInfo('configure#B', "Adjusting logLevel to ${settings.logLevel}")
    setLogLevel(settings.logLevel)
  }
  // Refresh numberOfButtons ...
  settings.numberOfButtons = countNumberOfButtons()
  logInfo('configure#C', "Before pbsg_configure(), ${showSettings()}")
  pbsg_configure(device.name)
  logInfo('configure#D', "After pbsg_configure(), ${showSettings()}")

  //=> sendEvent(
  //=>   name: 'testX',
  //=>   value: 'Dill Pickles',
  //=>   descriptionText: 'testX -> DillPickles',
  //=>   isStateChange: true
  //=> )
  //=> logInfo('updated', "testX (attribute): ${state.testX}, ${device.currentValue('testX')}")

}

void uninstalled() {
  // Runs on device tear down
  //-> setLogLevel(settings.logLevel)
  logInfo('uninstalled', "Taking no action, ${showSettings()}")
}


void initialize() {
  // Called on hub startup (per capability "Initialize"). This method
  // relies on configure() to process settings, create any missing child
  // devices and initialize PBSG operations and state updates.
  //-> (app ?: device).getLabel()
  //Map config = gatherPbsgConfigFromSettings() // Note: No Args (device)
  logInfo('initialize', "Before configure(), ${showSettings()}")
  configure() // Calls gatherPbsgConfigFromSettings(), saves state, publishes
  logInfo('initialize', "After configure(), ${showSettings()}")
  /*
  Map pbsg = [
    active: 'C',
    pushed: 3,
    lifo: ['E', 'A', 'B', 'D'],
    buttons: ['A', 'B', 'C', 'D', 'E'],
    numberOfButtons: 5,
    display: 'Placeholder'
  ]
  //state.pbsg = pbsg
  sendEvent(
    name: 'pbsg',
    value: pbsg,
    unit: 'not_applicable',
    descriptionText: 'active: C, button: 3',
    isStateChange: true
  )
  sendEvent(
    name: 'pushed',
    value: 3,
    descriptionText: 'active: C, button: 3',
    isStateChange: true
  )
  */
}

void refresh() {
  // On-demand state refresh per capability "Refresh"
  logInfo('refresh', "Taking no action, ${showSettings()}")
  /*
  Map pbsg = [
    active: 'C',
    pushed: 3,
    lifo: ['E', 'A', 'B', 'D'],
    buttons: ['A', 'B', 'C', 'D', 'E'],
    numberOfButtons: 5,
    display: 'Placeholder'
  ]
  sendEvent(
    name: 'pbsg',
    value: pbsg,
    unit: 'not_applicable',
    descriptionText: 'active: C, button: 3',
    isStateChange: false
  )
  sendEvent(
    name: 'pushed',
    value: 3,
    descriptionText: 'active: C, button: 3',
    isStateChange: true
  )
  */
}

void activate(String button) {
  pbsg = getPbsgState(device.name)
  pbsg_ActivateButton(pbsg, button)
  logInfo('activate', "After adjustment, ${showSettings()}")
  putPbsgState(pbsg)
}

void deactivate(String button) {
  pbsg = getPbsgState(device.name)
  pbsg_DeactivateButton(pbsg, button)
  logInfo('deactivate', "After adjustment, ${showSettings()}")
  putPbsgState(pbsg)
}

void activateLastActive() {
  pbsg = getPbsgState(device.name)
  pbsg_ActivateLastActive(pbsg)
  logInfo('activateLastActive', "After adjustment, ${showSettings()}")
  putPbsgState(pbsg)
}

// Process Methods on behalf of Child Devices

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
  DevW d = fetchVswWithToggle(button)
  d.parse([
    name: 'switch',
    value: 'on',
    descriptionText: "${d.name} was turned on",
    isStateChange: true
  ])
}

void turnOffVsw(String button) {
  DevW d = fetchVswWithToggle(button)
  d.parse([
    name: 'switch',
    value: 'off',
    descriptionText: "${d.name} was turned off",
    isStateChange: true
  ])
}

// Methods Expected for Advertised Capabilities

void push(Integer buttonNumber) {
  String button = state.bNumberToBName[buttonNumber]
  logInfo('push', "Received ${button} (#${buttonNumber})")
  activateButton(button) && syncVswsAndIssueCallback()
}

// Supported, State-Changing Actions

// Methods in lPBSG.groovy
//   - UTILITY
//       - buttonStringToButtonList(String buttonsSettingsKey)
//       - switchState(DevW d)
//   - APP UI SUPPORT
//       - solicitPbsgConfig(String pbsgName)
//   - FETCHERS
//       - getOrCreatePBSG(String pbsgName)
//       - getOrCreateVswWithToggle(String pbsgName, String button)
//   - PBSG TO/FROM STATE
//       - getPbsgState(String pbsgName)
//       - putPbsgState(Map pbsg)
//   - EXTRACT STATE FROM SETTINGS
//       - Map gatherPbsgStateFromConfig(String pbsgName, String instType = 'pbsg')
//       - Map pbsg_configure(Map pbsg)
//   - PBSG STATE CHANGING METHODS
//       - boolean pbsg_ActivateButton(Map pbsg, String button)
//       - boolean pbsg_DeactivateButton(Map pbsg, String button)
//       - boolean pbsg_ActivateLastActive(Map pbsg)
//       - boolean pbsg_EnforceDefault(Map pbsg)
//       - boolean pbsg_ReconcileVswsToState(Map pbsg)
//   - PBSG STATE VISIBILITY METHODS
//       - String pbsg_ButtonState(Map pbsg, String button)
//       - String pbsg_State(Map pbsg)
//   - NO LONGER REQUIRED
//       - PbsgChildVswEventHandler(Event e)
