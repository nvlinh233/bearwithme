import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PrimeVue from 'primevue/config'

import App from '../App.vue'

describe('App', () => {
  it('renders the app title and the start review button', () => {
    const wrapper = mount(App, {
      global: {
        plugins: [PrimeVue],
      },
    })

    expect(wrapper.text()).toContain('BearWithMe')
    expect(wrapper.find('button').text()).toContain('Start review')
  })
})
