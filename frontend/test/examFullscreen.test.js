import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'

// Execute the page's event handlers with browser state, including their reporting guard.
const page = readFileSync(new URL('../src/views/ExamTakeView.vue', import.meta.url), 'utf8')
const handlers = ['handleFullscreenChange', 'shouldReportSwitch', 'reportSwitchEvent']
  .map(name => page.match(new RegExp(`^function ${name}\\(.*$`, 'm'))[0]).join('\n')

function environment(started = true) {
  const context = {
    document: { documentElement: {}, fullscreenElement: null },
    antiCheat: { hasEnteredFullscreen: started, switchCount: 0 },
    examStarted: { value: started }, inProgress: { value: true },
    processId: 41, lastSwitchReportAt: 0, antiCheatQueue: [],
    flushAntiCheatQueue() {}, ElMessage: { warning() {} },
  }
  vm.createContext(context)
  vm.runInContext(handlers, context)
  return context
}

test('exiting fullscreen reports one switch before pausing the exam', () => {
  const context = environment()
  vm.runInContext('handleFullscreenChange()', context)
  assert.equal(context.antiCheatQueue.length, 1)
  assert.equal(context.antiCheatQueue[0].eventType, 'FULLSCREEN_EXIT')
  assert.equal(context.antiCheat.switchCount, 1)
  assert.equal(context.examStarted.value, false)
  vm.runInContext('handleFullscreenChange()', context)
  assert.equal(context.antiCheatQueue.length, 1)
})

test('entering fullscreen starts the exam without counting a switch', () => {
  const context = environment(false)
  context.document.fullscreenElement = context.document.documentElement
  vm.runInContext('handleFullscreenChange()', context)
  assert.equal(context.examStarted.value, true)
  assert.equal(context.antiCheatQueue.length, 0)
})
