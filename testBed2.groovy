// ---------------------------------------------------------------------------------
// T E S T B E D 2
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
// For reference:
//   Unicode 2190 ← LEFTWARDS ARROW
//   Unicode 2192 → RIGHTWARDS ARROW

#include WesMC.lUtils
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput as JsonOutput
import groovy.json.JsonSlurper as JsonSlurper
import groovy.transform.Field
import java.lang.Math as Math
import java.lang.Object as Object
import java.util.concurrent.ConcurrentHashMap

// The Groovy Linter generates NglParseError on Hubitat #include !!!

definition (
  name: 'TestBed2',
  namespace: 'WesMC',
  author: 'Wesley M. Conner',
  description: 'TestBed1',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestBed2')
}

Map TestBed2() {
  return dynamicPage(
    name: 'TestBed2',
    title: 'TestBed2',
    install: true,
    uninstall: true
  ) {
    app.updateLabel("TestBed2 (${app.id})")
    section {
      paragraph([
        h1('Header1'),
        b('bold'),
        bi('bold italic')
      ].join('<br/>'))
    }
  }
}

void installed() {
  setLogLevel('TRACE')
  logInfo('installed', [
    h1('Header1'),
    b('bold'),
    bi('bold italic')
  ].join('<br/>'))
}
