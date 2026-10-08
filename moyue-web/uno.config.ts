import { defineConfig, presetUno, presetAttributify, presetIcons } from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({ scale: 1.2 })
  ],
  theme: {
    colors: {
      primary: '#5b7fff',
      sidebar: '#1f2430'
    }
  },
  shortcuts: {
    'flex-center': 'flex items-center justify-center',
    'page-container': 'p-4 box-border'
  }
})
