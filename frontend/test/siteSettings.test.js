import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  DEFAULT_SITE_SETTINGS,
  normalizeSiteSettings,
  safeBrandAssetUrl,
  siteInitials,
} from '../src/utils/siteSettings.js'

test('normalizes supported site settings fields', () => {
  assert.deepEqual(normalizeSiteSettings({
    logoUrl: ' https://cdn.example.com/logo.png ',
    siteTitle: ' Example HR ',
    siteSubtitle: ' People operations ',
    footerHtml: ' <b>Rendered as text</b> ',
  }), {
    logoUrl: 'https://cdn.example.com/logo.png',
    siteTitle: 'Example HR',
    siteSubtitle: 'People operations',
    footerHtml: '<b>Rendered as text</b>',
    footerCode: '',
    copyright: '',
  })
})

test('accepts root-relative or HTTPS logos and rejects unsafe protocols', () => {
  assert.equal(safeBrandAssetUrl('/brand/logo.svg'), '/brand/logo.svg')
  assert.equal(safeBrandAssetUrl('https://cdn.example.com/logo.svg'), 'https://cdn.example.com/logo.svg')
  assert.equal(safeBrandAssetUrl('http://cdn.example.com/logo.svg'), '')
  assert.equal(safeBrandAssetUrl('javascript:alert(1)'), '')
  assert.equal(safeBrandAssetUrl('//cdn.example.com/logo.svg'), '')
  assert.equal(safeBrandAssetUrl('/\\evil.example/logo.svg'), '')
})

test('blank required fields fall back while footer remains plain text data', () => {
  const settings = normalizeSiteSettings({
    siteTitle: ' ',
    siteSubtitle: null,
    footerHtml: '<script>alert(1)</script>',
  })

  assert.equal(settings.siteTitle, DEFAULT_SITE_SETTINGS.siteTitle)
  assert.equal(settings.siteSubtitle, DEFAULT_SITE_SETTINGS.siteSubtitle)
  assert.equal(settings.footerHtml, '<script>alert(1)</script>')
  assert.equal(siteInitials(settings.siteTitle), '人工')
})

test('global footer is interpolated and never rendered with v-html', () => {
  const appView = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
  const footerView = readFileSync(new URL('../src/components/SiteFooter.vue', import.meta.url), 'utf8')

  assert.match(appView, /<SiteFooter\s*\/>/)
  assert.match(footerView, /\{\{\s*siteSettings\.footerHtml\s*\}\}/)
  assert.doesNotMatch(footerView, /v-html/)
})

test('long custom footer content stays inside the global footer', () => {
  const footerView = readFileSync(new URL('../src/components/SiteFooter.vue', import.meta.url), 'utf8')

  assert.match(footerView, /\.footer-copy \{[^}]*overflow-wrap:\s*anywhere/)
  assert.match(footerView, /sandbox=""/)
})
