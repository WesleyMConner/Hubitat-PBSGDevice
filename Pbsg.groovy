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
    capability "PushableButton"  // Attributes
                                 //   - numberOfButtons: number
                                 //   - pushed: number
                                 // Methods
                                 //   - push(number)
    // command "configure"
    // attribute "tbd"
  }
  preferences {
    // The following inputs:
    //   - Correspond to settings."${name}" (or `name` for short).
    //   - Are readable/writable on Hubitat's device drilldown page.
    //   - The settings function as configuration data for the driver.
    input(
      name: 'buttonsString',
      title: 'Space-delimited list of button names',
      type: 'text',
      required: true
    )
    input(
      name: 'numberOfButtons',
      title: 'Button count in buttonsString',
      type: 'number',
      defaultValue: buttonsString?.tokenize(' ')?.size(),
      required: true
    )
    input(
      name: "dflt",
      title: "Default button",
      type: "enum",
      multiple: false,
      options: [*buttonNames?.tokenize(' '), 'not applicable'],
      defaultValue: 'not applicable',
      required: true
    )
    input(
      name: "instType",
      title: "Type of PBSG",
      type: "text",
      defaultValue: "pbsg",
      required: true
    )
    input(
      name: "logLevel",
      title: "Logging Threshold Level",
      type: "enum",
      multiple: false,
      options: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],
      defaultValue: 'INFO',
      required: true
    )
  }
}

// Per https://docs2.hubitat.com/en/developer/driver/overview,
// device data can be managed using state and/or atomicState

void installed() {                          // Initial device configuration
  setLogLevel(settings.logLevel)
  logInfo('installed', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
}

void updated() {                            // Revised device configuration
  setLogLevel(settings.logLevel)
  logInfo('updated', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
}

void uninstalled() {                                    // Tear down device
  logInfo('uninstalled', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
}

void initialize() {                               // Begin device operation
  logInfo('initialize', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))
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
  logWarn('parse', "parse(String) ignored:<br/>'${text}'<")
  return null
}

void parse(ArrayList actions) {
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
