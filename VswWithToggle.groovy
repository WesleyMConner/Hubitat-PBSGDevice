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
import groovy.json.JsonOutput    // Appears in wesmc.lPBSG
import groovy.json.JsonSlurper   // Appears in wesmc.lPBSG

metadata {
  definition(
    name: 'VswWithToggle',
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    importUrl: 'PENDING',
    singleThreaded: 'false'
  ) {
    capability "Configuration"   // Methods
                                 //   - configure()
    capability "Switch"     // Attribtes
                            //   - switch: ['on'|'off']
                            // Methods
                            //   - on()
                            //   - off()
    capability "Momentary"  // Methods
                            //   - push()
  }
  preferences {
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

// Lifecycle methods

void installed() {
  // Runs when driver is installed
  //-> setLogLevel(settings.logLevel)
  logInfo('installed', 'Entered')
  settings?.logLevel && setLogLevel(settings.logLevel)
  //-> logInfo('installed', ['',
  //->   settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  //-> ].join('<br/>'))
}

void configure() {
  logInfo('configure', 'Entered')
  // Runs due to presence of capability "Configuration"
  settings?.logLevel && setLogLevel(settings.logLevel)
  //-> setLogLevel(settings.logLevel)
  //-> logInfo('configure', ['',
  //->   settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  //-> ].join('<br/>'))
}


void updated() {
  logInfo('updated', 'Entered')
  // Runs when save is clicked in the preferences section
  //-> setLogLevel(settings.logLevel)
  //-> logInfo('updated', ['',
  //->   settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  //-> ].join('<br/>'))
}

void uninstalled() {
  logInfo('uninstalled', 'Entered')
  // Runs on device tear down
  //-> setLogLevel(settings.logLevel)
  //-> logInfo('uninstalled', ['',
  //->   settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  //-> ].join('<br/>'))
}

void initialize() {
  logInfo('initialize', 'Entered')
  // Runs when called, typically by installed() and updated()
  //-> setLogLevel(settings.logLevel)
  //-> logInfo('initialize', ['',
  //->   settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  //-> ].join('<br/>'))
}

// Methods Expected for Advertised Capabilities

void on() { parent?.vswWithToggleOn(this.device) }

void off() { parent?.vswWithToggleOff(this.device) }

void push() { parent?.vswWithTogglePush(this.device) }

// Supported, State-Changing Actions

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
  logInfo('parse', "actions: ${actions}")
  actions.each{ action ->
    if (action?.name in ['switch']) {
      logInfo('parse', action.descriptionText)
      sendEvent(action)
    } else {
      logWarn('parse', "Ignored ${action}")
    }
  }
}

// Supporting functions
