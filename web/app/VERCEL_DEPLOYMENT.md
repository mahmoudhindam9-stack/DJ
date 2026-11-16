# Vercel deployment

This project is a Vite/React application with Node.js API routes.

## Vercel project settings
- **Root Directory:** `app`
- **Framework Preset:** Vite
- **Build Command:** `npm run build`
- **Output Directory:** `dist`
- **Install Command:** `npm ci`

The `api/[...path].js` function forwards the app's `/api/*` requests to the existing Node API handler. Online catalog/search/stream/download requests use this function.

**Web deployment limitation:** the desktop-only ACE-Step local music generator, Windows update installer, and any direct access to local folders or Windows processes cannot run on Vercel's serverless runtime. Those features remain specific to the Windows desktop build. Vercel must be connected to the GitHub repository to perform an actual production deployment.
