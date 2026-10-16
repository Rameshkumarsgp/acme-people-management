// Forwards /api to the backend during `ng serve`, so the browser sees one origin and needs no CORS.
// The backend defaults to port 8080. Point at another one with: API_TARGET=http://localhost:8081 npm start
export default {
  '/api': {
    target: process.env.API_TARGET ?? 'http://localhost:8080',
    secure: false,
  },
};
