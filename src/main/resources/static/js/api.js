/**
 * api.js — All communication with the Java Spring Boot backend.
 * Every function returns a Promise resolving to parsed JSON.
 *
 * The backend uses HTTP Basic Auth: credentials are sent as a
 * Base64-encoded "email:password" header on every request after login.
 *
 * For production, replace with JWT token-based auth.
 */

const API = (() => {

  // Stored after login — used for Authorization header
  let _credentials = null;   // { email, password, encoded }

  // ----------------------------------------------------------------
  //  Internal fetch helper
  // ----------------------------------------------------------------

  async function _fetch(method, url, body = null) {
    const headers = { 'Content-Type': 'application/json' };
    if (_credentials) {
      headers['Authorization'] = 'Basic ' + _credentials.encoded;
    }
    const opts = { method, headers };
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(url, opts);

    // Parse JSON for all responses
    let data;
    try { data = await res.json(); } catch { data = {}; }

    if (!res.ok) {
      const msg = data.error || data.message || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return data;
  }

  const get    = (url)       => _fetch('GET',    url);
  const post   = (url, body) => _fetch('POST',   url, body);
  const put    = (url, body) => _fetch('PUT',    url, body);
  const del    = (url)       => _fetch('DELETE', url);

  // ----------------------------------------------------------------
  //  Auth
  // ----------------------------------------------------------------

  async function login(email, password) {
    const encoded = btoa(email + ':' + password);
    // Temporarily set credentials so the POST goes through Basic Auth
    _credentials = { email, password, encoded };
    try {
      const user = await post('/api/auth/login', { email, password });
      // Persist credentials for subsequent requests
      sessionStorage.setItem('routex_creds', JSON.stringify(_credentials));
      return user;
    } catch (err) {
      _credentials = null;
      throw err;
    }
  }

  async function register(name, email, password) {
    const user = await post('/api/auth/register', { name, email, password });
    const encoded = btoa(email + ':' + password);
    _credentials = { email, password, encoded };
    sessionStorage.setItem('routex_creds', JSON.stringify(_credentials));
    return user;
  }

  function logout() {
    _credentials = null;
    sessionStorage.removeItem('routex_creds');
    sessionStorage.removeItem('routex_user');
  }

  async function getMe() {
    return get('/api/auth/me');
  }

  function restoreSession() {
    const stored = sessionStorage.getItem('routex_creds');
    if (stored) {
      try { _credentials = JSON.parse(stored); } catch { _credentials = null; }
    }
  }

  function isLoggedIn() { return _credentials !== null; }

  // ----------------------------------------------------------------
  //  Buses (§4.1 Search, §4.2 Seat Management)
  // ----------------------------------------------------------------

  function getCities() {
    return get('/api/buses/cities');
  }

  function searchBuses(from, to) {
    return get(`/api/buses/search?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
  }

  function getSeats(busId, date) {
    return get(`/api/buses/${busId}/seats?date=${date}`);
  }

  function getAllBuses() {
    return get('/api/buses');
  }

  // ----------------------------------------------------------------
  //  Bookings (§4.1, §4.3)
  // ----------------------------------------------------------------

  function createBooking(payload) {
    // payload: { busId, travelDate, seats, passengerName, passengerPhone, passengerAge }
    return post('/api/bookings', payload);
  }

  function getMyBookings() {
    return get('/api/bookings');
  }

  function getBooking(ref) {
    return get(`/api/bookings/${ref}`);
  }

  function cancelBooking(ref) {
    return del(`/api/bookings/${ref}`);
  }

  // ----------------------------------------------------------------
  //  Admin
  // ----------------------------------------------------------------

  function getAdminStats()    { return get('/api/admin/stats');    }
  function getAllBookings()    { return get('/api/admin/bookings'); }
  function getAllUsers()       { return get('/api/admin/users');    }
  function getAdminBuses()     { return get('/api/admin/buses');    }
  function createAdminBus(bus) { return post('/api/admin/buses', bus); }
  function updateAdminBus(id, bus) { return put(`/api/admin/buses/${id}`, bus); }
  function deleteAdminBus(id)  { return del(`/api/admin/buses/${id}`); }

  // ----------------------------------------------------------------
  //  Public API
  // ----------------------------------------------------------------

  return {
    login, register, logout, getMe, restoreSession, isLoggedIn,
    getCities, searchBuses, getSeats, getAllBuses,
    createBooking, getMyBookings, getBooking, cancelBooking,
    getAdminStats, getAllBookings, getAllUsers,
    getAdminBuses, createAdminBus, updateAdminBus, deleteAdminBus
  };

})();
