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
#include wesmc.lUtils  // Requires the following imports.
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
    namespace: 'wesmc',
    author: 'Wesley M. Conner',
    importUrl: 'PENDING',
    singleThreaded: 'false'
  ) {
    capability "Switch"          // Attribtes:
                                 //   - switch: ['on'|'off']
                                 // Commands: on(), off()
    capability "Momentary"       // Commands: push()
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
  // Called when a bare device is first constructed.
  logTrace('installed#56', "this.device.class: ${this.device.class}")
  logTrace('installed', 'Called, taking no action')
  settings?.logLevel && setLogLevel(settings.logLevel)
}

void uninstalled() {
  // Called on device tear down.
  logTrace('uninstalled', 'Called, taking no action')
}

void initialize() {
  // Called on hub startup (per capability "Initialize").
  logTrace('initialize', 'Called, taking no action')
}

void updated() {
  logTrace('updated', 'Entered')
  // Runs when save is clicked in the preferences section
  //-> setLogLevel(settings.logLevel)
  logInfo('updated', ['',
    settings.collect{k, v -> "${b(k)}: ${v}"}.join('<br/>')
  ].join('<br/>'))

}

// Methods Expected for Advertised Capabilities

void on() { parent?.vswWithToggleOn(this.device) }

void off() { parent?.vswWithToggleOff(this.device) }

void push() { parent?.vswWithTogglePush(this.device) }

// Supported, State-Changing Actions

void parse(String action) {
  // The String version of parse IS NOT supported
  logWarn('parse', [
    'parse(String) is not implemented',
    "Ignored action: ${action}"
  ].join('<br/>'))
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
  logTrace('parse', "actions: ${actions}, class: ${this.class}")
  actions.each{ action ->
    // Some actions can be passed directly to sendEvent()
    if (action?.name in ['switch']) {
      logTrace('parse', action.descriptionText)
      sendEvent(action)
    }
    // A config change requires a custom implementation
    else if (action?.name == 'logLevel') {
      device.updateSetting(
        'logLevel', [value: action.value, type: 'Enum']
      )
    } else {
      logWarn('parse', "Ignored ${action}")
    }
  }
}
