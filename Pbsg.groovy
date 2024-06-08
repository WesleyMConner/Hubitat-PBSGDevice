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
//   State is managed The following summarizes a PBSG's state:
//     [
//                 active: STRING
//                 pushed: INTEGER    (expected per PushableButton)
//                   lifo: ARRAYLIST
//                buttons: ARRAYLIST  (redundant for convenience)
//        numberOfButtons: INTEGER    (expected per PushableButton)
//                display: STRING     (redundant see below)
//     ]
//   IMPORTANT
//     The ARRAYLIST implementation of "lifo" is inverted.
//       - push() is expected to PREPEND an item which pop() will retrieve
//       - instead push() APPENDS an item which pop() retrieves
//     The 'display' STRING exists as a convenience. It provides a visual
//     summary active & lifo (so is fully redundant with respect to state).

metadata {
  definition(
    // Map keys per https://docs2.hubitat.com/en/developer/driver/overview
    name: 'PBSG',
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    importUrl: 'PENDING',
    singleThreaded: 'true'
  ) {
    // Closute per https://docs2.hubitat.com/en/developer/driver/overview with:
    // capabilities, commands, attributes, and/or fingerprints
    capability 'Initialize'      // implied commands:
                                 //   - initialize()
    capability 'PushableButton'  // implied attributes:
                                 //   - numberOfButtons: number
                                 //   - pushed: number
                                 // implied commands:
                                 //   - push(number)
    capability 'Refresh'         // implied commands:
                                 //   - refresh()
    command 'activate', [[
      name: 'button',
      type: 'STRING',
      description: 'The button name to activate'
    ]]
    command 'deactivate', [[
      name: 'button',
      type: 'STRING',
      description: 'The button name to deactivate'
    ]]
    command 'activateLastActive', [[ /* no arguments */ ]]

    //=> attribute 'textX', 'string'
  }
  preferences {
    // The following inputs drive application configuration:
    //   - Correspond to settings."${name}" (or `name` for short).
    //   - Are readable/writable on Hubitat's device drilldown page.
    //   - The settings function as configuration data for the driver.
    input(
      name: 'Buttons',
      title: "${b('Button Names')} (space delimited)",
      type: 'text',
      required: true
    )
    input(
      name: 'dflt',
      title: b('Default Button'),
      type: 'enum',
      multiple: false,
      options: (settings?.Buttons?.tokenize(' ') ?: []) << 'not_applicable',
      defaultValue: 'not_applicable',
      required: true
    )
    input(
      name: 'instType',
      title: b('Type of PBSG'),
      type: 'text',
      defaultValue: 'pbsg',
      required: true
    )
    input(
      name: 'logLevel',
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

void installed() {
  // Called when device is first added
  //-> setLogLevel(settings.logLevel)
  logInfo('installed', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
}

/*
void configure() {
  // Runs due to presence of capability "Configuration"
  //-> setLogLevel(settings.logLevel)
  logInfo('configure', 'at entry')
}
*/

void updated() {
  // Called when 'Save Preferences' is pushed on the Device drilldown page.
  // Review changes to 'settings':
  //   - Ensure the overall consistency of settings
  //   - Adjust 'state' as required.
  // NOTES
  //   - device.removeSetting('<key>')
  setLogLevel(settings.logLevel)
  // Refresh numberOfButtons ...
  settings.numberOfButtons = settings?.Buttons?.tokenize(' ')?.size() ?: 0
  logInfo('updated', [b('SETTINGS:'),
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
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
  logInfo('uninstalled', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
}

void initialize() {
  // Called on hub startup (if driver specifies capability "Initialize")
  // Otherwise: It is not required or automatically called if present.
  //-> setLogLevel(settings.logLevel)

  logInfo('initialize', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
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
  logInfo('refresh', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
    ].join('<br/>'))
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

void parse(String text) {
  // Exposed externally
  // Note:
  //    parse(String desc) can handle raw incoming data from Zigbee,
  //    Z-Wave, or LAN devices and generally creates events as needed;
  //    see example drivers for more (virtual devices generally do not
  //    demonstrate this behavior, as there is no data coming in from
  //    a real device)

  switch (text) {
    case 'alpha':
    case 'beta':
    case 'gamma':
      logInfo('parse', "Recognized >${text}<")
      break
    default:
      logInfo('parse', "Rejected >${text}<")
  }
  return null
}

void parse(ArrayList actions) {
  // This implementation is for component devices and their parents.
  logWarn('parse', ['parse(ArrayList) ignored:',
    actions.join('<br/>')
  ].join('<br/>'))
}

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
//       - Map pbsg_BuildToConfig(Map pbsg)
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
