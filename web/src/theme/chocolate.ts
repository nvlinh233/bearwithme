import { definePreset } from '@primeuix/themes'
import Aura from '@primeuix/themes/aura'

// Brand palette from the v1 spec: chocolate #6E4030, dark #5A3325,
// milk brown #9C6A50, milk white #FBF8F5.
export const Chocolate = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#fbf8f5',
      100: '#f3e9e2',
      200: '#e6d2c4',
      300: '#d2b19c',
      400: '#b88a70',
      500: '#9c6a50',
      600: '#6e4030',
      700: '#5a3325',
      800: '#46281d',
      900: '#331d15',
      950: '#22130e',
    },
    colorScheme: {
      light: {
        primary: {
          color: '{primary.600}',
          hoverColor: '{primary.700}',
          activeColor: '{primary.800}',
        },
      },
    },
  },
})
