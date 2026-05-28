const auth = require('./utils/auth.js');

App({
  globalData: {
    user: null,
    home: null
  },

  onLaunch() {
    this.globalData.user = auth.getUser();
  }
});
