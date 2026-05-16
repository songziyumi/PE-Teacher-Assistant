const auth = require('./utils/auth');

App({
  globalData: {
    user: null,
    home: null
  },

  onLaunch() {
    this.globalData.user = auth.getUser();
  }
});
