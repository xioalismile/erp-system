import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        browser: true,
        node: true,
        defineProps: 'readonly',
        defineEmits: 'readonly',
        defineExpose: 'readonly',
        withDefaults: 'readonly'
      }
    },
    rules: {
      'no-console': ['warn', {
        allow: ['warn', 'error']
      }],
      'no-debugger': 'warn',
      'no-unused-vars': 'off',
      'no-undef': 'off',
      'no-empty': 'off',
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'warn',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/first-attribute-linebreak': 'off',
      'vue/html-indent': 'off',
      'vue/attributes-order': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/html-closing-bracket-spacing': 'off',
      'vue/no-multiple-template-root': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/prop-name-casing': 'off',
      'vue/component-definition-name-casing': 'off',
      'vue/v-on-event-hyphenation': 'off'
    }
  },
  {
    ignores: ['dist/**', 'node_modules/**', '*.config.js']
  }
]
