# WeChat Miniapp

This directory contains a native WeChat Mini Program skeleton for the PE Teacher Assistant system.

## Current scope

- Login page
- Role-aware home page
- API client for `/api/miniapp/**`

## Backend requirement

The backend should expose the miniapp endpoints added on branch `feature/wechat-miniapp-api`.

Default API base:

- `https://www.jsqyty.com`

You can change it in:

- `utils/config.js`

## Open in WeChat DevTools

1. Open the `miniapp` directory in WeChat DevTools.
2. Set a real `appid` in `project.config.json` when needed.
3. Make sure the backend domain is configured in the mini program request whitelist.

## Implemented pages

- `pages/login/index`
- `pages/home/index`
