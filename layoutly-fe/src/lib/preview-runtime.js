import * as React from 'react'
import * as ReactDOM from 'react-dom/client'
import * as ReactDOMLegacy from 'react-dom'
import * as Router from 'react-router-dom'
// This bundle runs as a script inside the sandboxed preview, never as a worker.
// MemoryRouter keeps routing inside the opaque-origin srcdoc iframe, whose
// about:srcdoc URL cannot support browser history or HashRouter URLs.
globalThis.__layoutlyRuntime = { React, ReactDOM, ReactDOMLegacy, Router: { ...Router, BrowserRouter: Router.MemoryRouter, HashRouter: Router.MemoryRouter } }
globalThis.React = React
