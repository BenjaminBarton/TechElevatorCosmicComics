import axios from 'axios';

export default {

  login(user) {
    return axios.post('/login', user)
  },

  register(user) {
    return axios.post('/register', user)
  },

  getUserById(id) {
    return axios.get(`/user/${id}`)
  },

  getId() {
    return axios.get(`/user/get/id`)
  },

  getIdByUsername(username) {
    return axios.get(`/user/get/id/${username}`)
  }

}
