module.exports = {
  root: true,
  ignorePatterns: ['dist/', 'node_modules/'],
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  extends: ['eslint:recommended', 'plugin:vue/vue3-essential'],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  rules: {
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    'no-constant-condition': ['error', { checkLoops: false }],
    'vue/multi-word-component-names': 'off',
  },
}
