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
#include wesmc.lUtils
#include wesmc.lPBSG

metadata {
  definition(
    name: 'VswWithToggle',
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    importUrl: 'PENDING',
    singleThreaded: 'false'
  ) {
    capability "Switch"     // switch: ['on'|'off'], on(), off()
    capability "Momentary"  // push()
  }
  preferences {
    input name: "logLevel", title: "Logging Threshold Level",
      required: true,
      type: "enum", options: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],
      multiple: false, defaultValue: 'INFO'
  }
}

// Lifecycle methods

void installed() { initialize() }

void uninstalled() { /* No action */ }

void updated() { initialize() }

void initialize() { setLogLevel(logLevel) }

// Externally-called methods

void on() { parent?.vswWithToggleOn(this.device) }

void off() { parent?.vswWithToggleOff(this.device) }

void push() { parent?.vswWithTogglePush(this.device) }

// Parent-directed actions

Event parse(String action) {
  // The String version of parse IS NOT supported
  logWarn('parse', [
    'parse(String) is not implemented',
    "Ignored action: ${action}"
  ].join('<br/>'))
  return null
}

void parse(ArrayList actions) {
  // Each action is a Map that is compatible with sendEvent(Map m).
  //   - sendEvent() sets the named attribute AND publishes the event
  //   - Map keys:
  //                  name: device attribute
  //                 value: attribute's value
  //                  unit: Omitted if boolean, '%', ...
  //       descriptionText: Human-friendly string
  //         isStateChange: true|false
  actions.each{ action ->
    if (action.name in ['switch']) {
      logInfo('parse', action.descriptionText)
      sendEvent(action)
    } else {
      logWarn('parse', "Ignored ${action}")
    }
  }
}

// Supporting functions
