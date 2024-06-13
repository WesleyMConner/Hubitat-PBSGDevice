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
// Referenced types below
//   - import com.hubitat.app.DeviceWrapper as DevW
//   - import com.hubitat.hub.domain.Event as Event
//   - import groovy.json.JsonOutput
//   - import groovy.json.JsonSlurper
// The following are required when using this library.
//   - #include wesmc.lUtils

library(
  name: 'lPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

// -------
// UTILITY
// -------

String switchState(DevW d) {
  return d.currentValue('switch', true) // true -> Do not use the cache
}

// -----------------------------------------------
// UI DATA SOLICITATION AND OPERATIONS ON SETTINGS
// -----------------------------------------------
//
//   +------------+-----------------------+-----------------------|
//   |    CONTEXT |          App          |        Device         |
//   +------------|-----------------------+-----------------------|
//   | WRITE DATA |      via input()      |    via sendEvent()    |
//   +------------------------------------+-----------------------|
//   |  READ DATA |             via settings."${key}"             |
//   +------------+-----------------------+-----------------------|
//   |   prefix = |    Populated with     |         null          |
//   |            |    "${PbsgName}_"     |                       |
//   +------------+-----------------------+-----------------------|
//   |  dynamic = |      (device?.getName()) ? false : true       |
//   |            |    [dynamic only applies in an App context]   |
//   +------------+-----------------------+-----------------------|


// --------
// FETCHERS
// --------

DevW getOrCreatePBSG(String pbsgName) {
  String dni = pbsgName   // Should NOT contain special chars except for '_'
  String name = dni.replaceAll('_', ' ') // Delim ' ' avoids special chars
  // Device Network Identifiers DO NOT include white space.
  // Device Names (exposed to Alexa ...) DO NOT include special characters.
  //-> logInfo(
  //->   'getOrCreatePBSG#A', ['',
  //->     "pbsgName: ${pbsgName}",
  //->     "dni: ${dni}",
  //->     "name: ${name}"
  //-> ].join('<br/>'))
  DevW d = getChildDevice(dni)
  if (d) {
    //-> logInfo('getOrCreatePBSG#B', "d: ${d}")
  } else {
    logWarn('getOrCreatePBSG', "Creating PBSG ${b(dni)}")
    d = addChildDevice(
      'wesmc',   // namespace
      'PBSG',    // typeName
      dni,       // Device Network Identifier (<pbsgName>_<button>)
      [
        isComponent: true,   // Lifecycle is tied to parent
        name: name,          // "PBSG <pbsgName>"
        label: name          // "PBSG <pbsgName>"
      ]
    )
  }
  return d
}

DevW getOrCreateVswWithToggle(String pbsgName, String button) {
  // Device Network Identifiers DO NOT include white space.
  // Device Names (exposed to Alexa ...) DO NOT include special characters.
  String dni = "${pbsgName}_${button}"
  String name = dni.replaceAll('_', ' ') // Delim ' ' avoids special chars
  DevW d = getChildDevice(dni)
  logInfo('getOrCreateVswWithToggle#A', ['',
    "pbsgName: ${pbsgName}",
    "button: ${button}",
    "dni: ${dni}",
    "name: ${name}",
    "d: ${d}"
  ].join('<br/>'))
  if (!d) {
    logWarn('getOrCreateVswWithToggle', "Creating VswWithToggle ${b(dni)}")
    d = addChildDevice(
      'wesmc',           // namespace
      'VswWithToggle',   // typeName
      dni,               // Device Network Identifier (<pbsgName>_<button>)
      [
        isComponent: true,   // Lifecycle is tied to parent
        name: name,          // "PBSG <pbsgName>"
        label: name          // "PBSG <pbsgName>"
      ]
    )
    logInfo('getOrCreateVswWithToggle#A', ['',
      "pbsgName: ${pbsgName}",
      "button: ${button}",
      "dni: ${dni}",
      "name: ${name}",
      "d: ${d}"
    ].join('<br/>'))
  }
  return d
}

// ------------------
// PBSG TO/FROM STATE
// ------------------


Integer getCurrentButton(Map pbsg) {
  String active = pbsg?.active
  return active ? pbsg.buttonToPosition(active) : null
}

// ---------------------------
// PBSG STATE CHANGING METHODS
// ---------------------------

void pbsg_ReconcileVswsToState(Map pbsg) {
  // Assumed: pbsg != null
  // The supplied PBSG is used without making any changes to its state.
  // Get or Create the child VSW for each button.
  Map buttonToVsw = [:]
  pbsg?.all.each { button ->
    buttonToVsw << [ (button) : getOrCreateVswWithToggle(pbsg.name, button) ]
  }
  // Ensure VSW state matches button state.
  buttonToVsw.each { button, vsw ->
    if (pbsg.active == button) {
      if ( switchState(vsw) != 'on') { vsw.on() }
    } else {
      if ( switchState(vsw) != 'off') { vsw.off() }
    }
  }
}

// -----------------------------
// PBSG STATE VISIBILITY METHODS
// -----------------------------

