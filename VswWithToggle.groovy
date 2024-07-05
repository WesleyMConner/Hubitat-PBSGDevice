// ---------------------------------------------------------------------------------
// V S W   W I T H   T O G G L E
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
    name: 'VswWithToggle',
    namespace: 'WesMC',
    author: 'Wesley M. Conner',
    description: 'This device is a sub-component of device WesMC.PBSG',
    category: '',   // As of Q2'24 Not used
    iconUrl: '',    // As of Q2'24 Not used
    iconX2Url: '',  // As of Q2'24 Not used
    documentationLink: 'A Hubitat Community post is pending',
    importUrl: 'https://github.com/WesleyMConner/Hubitat-PBSGLibrary',
    singleThreaded: 'false'
  ) {
    capability "Switch"          // Attribtes:
                                 //   - switch: ['on'|'off']
                                 // Commands: on(), off()
    capability "Momentary"       // Commands: push()
  }
  preferences { /* THIS DEVICE IS FULLY MANAGED BY THE PARENT DEVICE */ }
}

// COMMANDS FOR ADVERTISED CAPABILITIES

void on(String ref = null) {
  if (parent.settings.logVswActivity) { logTrace('on', 'Received on()') }
  parent.cmdQueue().offer([
    name: 'Activate',
    arg: parent.vswToButtonName(this.device),
    ref: ref,
    version: parent.csm().version
  ], 2, SECONDS) || logError('off', 'Could not queue "Activate".')
}

void off(String ref = null) {
  if (parent.settings.logVswActivity) { logTrace('on', 'Received off()') }
  parent.cmdQueue().offer([
    name: 'Deactivate',
    arg: parent.vswToButtonName(this.device),
    ref: ref,
    version: parent.csm().version
  ], 2, SECONDS) || logError('off', 'Could not queue "Deactivate".')
}

void push(String ref = null) {
  if (parent.settings.logVswActivity) { logTrace('on', 'Received push()') }
  parent.cmdQueue().offer([
    name: 'Toggle',
    arg: parent.vswToButtonName(this.device),
    ref: ref,
    version: parent.csm().version
  ], 2, SECONDS) || logError('off', 'Could not queue "Toggle".')
}

// EXPECT PARENT TO MANIPULATE THIS DEVICE

void parse(ARRAYLIST ACTIONS) {
  // This command expects actions (an ArrayList) of commands (Maps).
  // Each Map must be suitable for execution by sendEvent().
  //      +-----------------+-------------------------+
  //      |            name | Target device attribute |
  //      |           value | Attribute's value       |
  //      | descriptionText | Human-friendly string   |
  //      |   isStateChange | true or false           |
  //      |            unit | NOT REQUIRED            |
  //      +-----------------+-------------------------+
  // PROCESS THE LIST OF ACTIONS
  ArrayList allowedActions = ['switch']
  actions.each{ action ->
    if (parent.settings.logVswActivity) {
      logTrace('parse', "Received action: ${bMap(action)}")
    }
    if (action?.name in allowedActions) { sendEvent(action) }
  }
}

// NOT REQUIRED
//   void installed() { }
//   void uninstalled() { }
//   void initialize() {}
//   void updated() { }
//   void parse(String) { }
