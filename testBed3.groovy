// ---------------------------------------------------------------------------------
// T E S T B E D 3
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

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include WesMC.lUtils
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput as JsonOutput
import groovy.json.JsonSlurper as JsonSlurper
import java.lang.Math as Math
import java.lang.Object as Object

import groovy.transform.Field
import java.util.concurrent.SynchronousQueue
import java.time.Instant
import java.time.Duration

@Field static SynchronousQueue<Map> q = [:]

definition (
  name: 'TestBed3',
  namespace: 'WesMC',
  author: 'Wesley M. Conner',
  description: 'TestBed3',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestBed3')
}

Map TestBed3() {
  return dynamicPage(
    name: 'TestBed3',
    title: 'TestBed3',
    install: true,
    uninstall: true
  ) {
    app.updateLabel("TestBed3 (${app.id})")
    section{
      paragraph h1('Passing Maps Test')
      paragraph h2('No Insulation')
      Map a = [a: 'one', b: 'two', c: 'three']
      paragraph "a: ${a}"
      appendToMap(a)
      paragraph "a: ${a}"
      removeFromMap(a)
      paragraph "a: ${a}"
      alterMap(a)
      paragraph "a: ${a}"
      paragraph h2('Insulated Copy')
      a = [a: 'one', b: 'two', c: 'three']
      paragraph "a: ${a}"
      isolatedAppend(a)
      paragraph "a: ${a}"
      isolatedRemove(a)
      paragraph "a: ${a}"
      isolatedAlter(a)
      paragraph "a: ${a}"
    }
  }
}

void appendToMap(Map a) {
  x = a
  x << [g: 'seven']
  paragraph "In appendToMap, x: ${x}"
}

void removeFromMap(Map a) {
  x = a
  x.findAll { k, v -> (k == 'b') } each { k, v -> x.remove k }
  paragraph "In removeFromMap, x: ${x}"
}

void alterMap(Map a) {
  x = a
  x.b = 'TWO'
  paragraph "In alterMap, x: ${x}"
}

Map copyMap (Map x) {
  return x.findAll { k, v -> (k) }
}

void isolatedAppend(Map x) {
  localX = copyMap(x)
  localX << [g: 'seven']
  paragraph "In appendToMap, localX: ${localX}"
}

void isolatedRemove(Map x) {
  localX = copyMap(x)
  localX.findAll { k, v -> (k == 'b') } each { k, v -> localX.remove k }
  paragraph "In removeFromMap, localX: ${localX}"
}

void isolatedAlter(Map x) {
  localX = copyMap(x)
  localX.b = 'TWO'
  paragraph "In alterMap, localX: ${localX}"
}

void installed() {
  logInfo('installed', 'TBD ...')
}
