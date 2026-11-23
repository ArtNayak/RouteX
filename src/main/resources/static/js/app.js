/**
 * app.js — Main application controller.
 * Initialises the app, wires up auth, search, bookings, admin, and confirmation.
 * Depends on: api.js, ui.js, canvas.js, seats.js
 */

const App = (() => {

  let _currentUser = null;
  let _adminEditingBusId = null;
  let _adminSeatBusId = null;
  let _adminSeatDate = new Date().toISOString().split('T')[0];

  // ----------------------------------------------------------------
  //  Bootstrap
  // ----------------------------------------------------------------

  async function init() {
    Canvas.init();

    // Restore session from sessionStorage
    API.restoreSession();
    const storedUser = sessionStorage.getItem('routex_user');
    if (storedUser) {
      try {
        _currentUser = JSON.parse(storedUser);
        UI.updateNav(_currentUser);
        _updateHomeStats();
      } catch { /* ignore */ }
    }

    // Wire modal close on backdrop click
    document.getElementById('auth-modal')
      .addEventListener('click', e => { if (e.target.id === 'auth-modal') UI.closeAuthModal(); });

    // Populate city dropdowns
    await _loadCities();

    // Set today as default date
    const dateInput = document.getElementById('search-date');
    dateInput.min   = new Date().toISOString().split('T')[0];
    dateInput.value = new Date().toISOString().split('T')[0];

    _updateHomeStats();
    UI.showPage('home');
  }

  function getCurrentUser() { return _currentUser; }

  // ----------------------------------------------------------------
  //  City Dropdowns
  // ----------------------------------------------------------------

  async function _loadCities() {
    try {
      const cities = await API.getCities();
      ['search-from', 'search-to'].forEach(id => {
        const sel = document.getElementById(id);
        const cur = sel.value;
        sel.innerHTML = '<option value="">Select City</option>' +
          cities.map(c => `<option value="${c}">${c}</option>`).join('');
        if (cur) sel.value = cur;
      });
    } catch { /* Server may not be up in static preview */ }
  }

  // ----------------------------------------------------------------
  //  Home Stats
  // ----------------------------------------------------------------

  async function _updateHomeStats() {
    if (!API.isLoggedIn()) return;
    try {
      if (_currentUser?.role === 'ADMIN') {
        const stats = await API.getAdminStats();
        document.getElementById('stat-bookings').textContent = stats.totalBookings || 0;
      } else {
        const bookings = await API.getMyBookings();
        document.getElementById('stat-bookings').textContent = bookings.length || 0;
      }
    } catch { /* ignore */ }
  }

  // ----------------------------------------------------------------
  //  AUTH
  // ----------------------------------------------------------------

  async function handleLogin() {
    const email = document.getElementById('login-email').value.trim();
    const pass  = document.getElementById('login-pass').value;
    if (!email || !pass) { UI.toast('Please fill all fields', 'error'); return; }

    const btn = document.getElementById('login-submit');
    btn.disabled = true; btn.textContent = 'Signing in…';

    try {
      _currentUser = await API.login(email, pass);
      sessionStorage.setItem('routex_user', JSON.stringify(_currentUser));
      UI.updateNav(_currentUser);
      UI.closeAuthModal();
      UI.toast(`Welcome back, ${_currentUser.name}!`, 'success');
      _updateHomeStats();
    } catch (err) {
      UI.toast(err.message, 'error');
    } finally {
      btn.disabled = false; btn.textContent = 'Sign In';
    }
  }

  async function handleRegister() {
    const name  = document.getElementById('reg-name').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const pass  = document.getElementById('reg-pass').value;
    if (!name || !email || !pass) { UI.toast('Please fill all fields', 'error'); return; }
    if (pass.length < 6) { UI.toast('Password must be 6+ characters', 'error'); return; }

    const btn = document.getElementById('reg-submit');
    btn.disabled = true; btn.textContent = 'Creating…';

    try {
      _currentUser = await API.register(name, email, pass);
      sessionStorage.setItem('routex_user', JSON.stringify(_currentUser));
      UI.updateNav(_currentUser);
      UI.closeAuthModal();
      UI.toast(`Account created! Welcome, ${_currentUser.name}!`, 'success');
    } catch (err) {
      UI.toast(err.message, 'error');
    } finally {
      btn.disabled = false; btn.textContent = 'Create Account';
    }
  }

  function logout() {
    API.logout();
    _currentUser = null;
    sessionStorage.removeItem('routex_user');
    UI.updateNav(null);
    UI.showPage('home');
    UI.toast('Logged out successfully', 'info');
  }

  function requireAuth(page) {
    if (!_currentUser) { UI.openAuthModal('login'); return; }
    UI.showPage(page);
    if (page === 'bookings') renderBookings();
    if (page === 'admin')    renderAdmin();
  }

  // ----------------------------------------------------------------
  //  SEARCH  (§4.1)
  // ----------------------------------------------------------------

  async function searchBuses() {
    const from = document.getElementById('search-from').value;
    const to   = document.getElementById('search-to').value;
    const date = document.getElementById('search-date').value;

    if (!from || !to || !date) { UI.toast('Please fill all search fields', 'error'); return; }
    if (from === to)           { UI.toast('Source and destination cannot be the same', 'error'); return; }

    const results = document.getElementById('search-results');
    results.innerHTML = UI.loadingHTML();

    try {
      const buses = await API.searchBuses(from, to);

      if (!buses.length) {
        results.innerHTML = UI.emptyState('🚌', 'No buses found',
          'Try a different route or date.',
          `<button class="btn btn-primary" style="margin-top:24px" onclick="App.clearSearch()">Clear Search</button>`);
        return;
      }

      results.innerHTML =
        `<div style="margin-bottom:16px;color:var(--text2);font-size:14px">
           <strong style="color:var(--text)">${buses.length}</strong> bus${buses.length > 1 ? 'es' : ''} found
           for <strong style="color:var(--text)">${from} → ${to}</strong>
           on <strong style="color:var(--text)">${UI.formatDate(date)}</strong>
         </div>` +
        buses.map(bus => _busCardHTML(bus, date, from, to)).join('');

    } catch (err) {
      results.innerHTML = UI.emptyState('⚠️', 'Search failed', err.message);
    }
  }

  function _busCardHTML(bus, date, from, to) {
    const avail = bus.totalSeats - 5; // Rough estimate; precise count comes on seat page
    const seatsClass = avail < 8 ? 'seats-low' : 'seats-ok';
    const encodedBus = encodeURIComponent(JSON.stringify(bus));
    return `
      <div class="bus-result-card" onclick="App.selectBusEncoded('${encodedBus}', '${date}')">
        <div>
          <div class="bus-name">${bus.operatorName}</div>
          <div class="bus-type-badge">${bus.busType}</div>
        </div>
        <div>
          <div class="time-big">${bus.departureTime}</div>
          <div class="time-city">${from}</div>
        </div>
        <div class="duration-col">
          <div class="duration-text">${bus.duration}</div>
          <div class="duration-line"></div>
          <div class="duration-text">Non-stop</div>
        </div>
        <div>
          <div class="time-big">${bus.arrivalTime}</div>
          <div class="time-city">${to}</div>
        </div>
        <div>
          <div class="price-big">₹${bus.pricePerSeat}</div>
          <div class="price-sub">per seat</div>
          <div class="seats-left ${seatsClass}">${bus.totalSeats} seats total</div>
        </div>
        <button class="btn btn-primary btn-sm"
                onclick="event.stopPropagation();App.selectBusEncoded('${encodedBus}', '${date}')">
          Select Seats →
        </button>
      </div>`;
  }

  function clearSearch() {
    document.getElementById('search-from').value = '';
    document.getElementById('search-to').value   = '';
    document.getElementById('search-results').innerHTML = '';
  }

  function selectBus(busJSON, date) {
    if (!_currentUser) { UI.openAuthModal('login'); return; }
    const bus = typeof busJSON === 'string' ? JSON.parse(busJSON) : busJSON;
    Seats.render(bus, date);
    UI.showPage('seats');
  }

  function selectBusEncoded(encodedBus, date) {
    const bus = JSON.parse(decodeURIComponent(encodedBus));
    selectBus(bus, date);
  }

  // ----------------------------------------------------------------
  //  MY BOOKINGS
  // ----------------------------------------------------------------

  async function renderBookings() {
    const container = document.getElementById('bookings-list');
    container.innerHTML = UI.loadingHTML();
    try {
      const bookings = await API.getMyBookings();
      if (!bookings.length) {
        container.innerHTML = UI.emptyState('🎫', 'No bookings yet',
          'Book your first bus ticket!',
          `<button class="btn btn-primary" style="margin-top:24px" onclick="UI.showPage('search')">Search Buses →</button>`);
        return;
      }
      container.innerHTML = bookings.map(b => _bookingItemHTML(b)).join('');
    } catch (err) {
      container.innerHTML = UI.emptyState('⚠️', 'Failed to load bookings', err.message);
    }
  }

  function _bookingItemHTML(b) {
    const isConfirmed = b.status === 'CONFIRMED';
    return `
      <div class="booking-item">
        <div class="booking-status-dot ${isConfirmed ? 'status-confirmed' : 'status-cancelled'}"></div>
        <div class="booking-info">
          <div class="booking-route">${b.bus.fromCity} → ${b.bus.toCity}</div>
          <div class="booking-meta">
            ${b.bus.operatorName} · ${b.bus.busType}<br>
            ${UI.formatDate(b.travelDate)} · Departs ${b.bus.departureTime}<br>
            Seats: <strong>${b.seatNumbers}</strong> · ${b.passengerName}
          </div>
          <div style="margin-top:8px">
            <span class="badge ${isConfirmed ? 'badge-success' : 'badge-danger'}">${b.status}</span>
            <span style="font-size:12px;color:var(--text3);margin-left:8px">${b.bookingRef}</span>
          </div>
        </div>
        <div class="booking-price">₹${b.totalAmount}</div>
        <div class="booking-actions">
          ${isConfirmed
            ? `<button class="btn btn-danger btn-sm" onclick="App.cancelBooking('${b.bookingRef}')">Cancel</button>`
            : ''}
        </div>
      </div>`;
  }

  async function cancelBooking(ref) {
    if (!confirm('Cancel this booking? Seats will be released.')) return;
    try {
      await API.cancelBooking(ref);
      UI.toast('Booking cancelled. Seats released.', 'info');
      renderBookings();
    } catch (err) {
      UI.toast(err.message, 'error');
    }
  }

    // ----------------------------------------------------------------
  //  ADMIN DASHBOARD
  // ----------------------------------------------------------------

  function _adminBusPayload(prefix) {
    return {
      busNumber: document.getElementById(`${prefix}-bus-number`).value.trim(),
      operatorName: document.getElementById(`${prefix}-operator`).value.trim(),
      busType: document.getElementById(`${prefix}-type`).value.trim(),
      fromCity: document.getElementById(`${prefix}-from`).value.trim(),
      toCity: document.getElementById(`${prefix}-to`).value.trim(),
      departureTime: document.getElementById(`${prefix}-departure`).value.trim(),
      arrivalTime: document.getElementById(`${prefix}-arrival`).value.trim(),
      duration: document.getElementById(`${prefix}-duration`).value.trim(),
      pricePerSeat: Number(document.getElementById(`${prefix}-price`).value),
      totalSeats: Number(document.getElementById(`${prefix}-seats`).value)
    };
  }

  function _validateAdminBusPayload(payload) {
    if (!payload.busNumber || !payload.operatorName || !payload.busType ||
        !payload.fromCity || !payload.toCity || !payload.departureTime ||
        !payload.arrivalTime || !payload.duration) {
      return 'Please fill all bus fields.';
    }
    if (payload.fromCity.toLowerCase() === payload.toCity.toLowerCase()) {
      return 'From city and to city cannot be the same.';
    }
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(payload.departureTime)
      || !/^([01]\d|2[0-3]):[0-5]\d$/.test(payload.arrivalTime)) {
      return 'Departure and arrival times must use HH:mm format.';
    }
    if (!(payload.pricePerSeat > 0)) return 'Price per seat must be greater than 0.';
    if (!(payload.totalSeats > 0)) return 'Total seats must be at least 1.';
    return null;
  }

  function _adminBusFormHTML(mode, bus = {}) {
    const title = mode === 'edit' ? `Edit Bus #${bus.id}` : 'Add New Bus';
    const prefix = mode === 'edit' ? 'admin-edit' : 'admin-add';
    const actionButtons = mode === 'edit'
      ? `<button class="btn btn-primary btn-sm" onclick="App.updateBusAdmin(${bus.id})">Update Bus</button>
         <button class="btn btn-secondary btn-sm" onclick="App.cancelBusEdit()">Cancel</button>`
      : `<button class="btn btn-primary btn-sm" onclick="App.createBusAdmin()">Add Bus</button>`;

    return `
      <div class="table-card">
        <div class="table-header">
          <div class="table-title">${title}</div>
          <div class="table-count">Manage routes, prices and timings</div>
        </div>
        <div class="admin-bus-form">
          <div class="admin-bus-grid">
            <div class="form-group">
              <label class="form-label">Bus Number</label>
              <input class="form-input" id="${prefix}-bus-number" value="${bus.busNumber || ''}" placeholder="MH21">
            </div>
            <div class="form-group">
              <label class="form-label">Operator</label>
              <input class="form-input" id="${prefix}-operator" value="${bus.operatorName || ''}" placeholder="Shivneri">
            </div>
            <div class="form-group">
              <label class="form-label">Bus Type</label>
              <input class="form-input" id="${prefix}-type" value="${bus.busType || ''}" placeholder="AC Sleeper">
            </div>
            <div class="form-group">
              <label class="form-label">From City</label>
              <input class="form-input" id="${prefix}-from" value="${bus.fromCity || ''}" placeholder="Mumbai">
            </div>
            <div class="form-group">
              <label class="form-label">To City</label>
              <input class="form-input" id="${prefix}-to" value="${bus.toCity || ''}" placeholder="Pune">
            </div>
            <div class="form-group">
              <label class="form-label">Duration</label>
              <input class="form-input" id="${prefix}-duration" value="${bus.duration || ''}" placeholder="3h 30m">
            </div>
            <div class="form-group">
              <label class="form-label">Departure (HH:mm)</label>
              <input class="form-input" id="${prefix}-departure" value="${bus.departureTime || ''}" placeholder="06:30">
            </div>
            <div class="form-group">
              <label class="form-label">Arrival (HH:mm)</label>
              <input class="form-input" id="${prefix}-arrival" value="${bus.arrivalTime || ''}" placeholder="10:15">
            </div>
            <div class="form-group">
              <label class="form-label">Price / Seat</label>
              <input class="form-input" type="number" min="1" step="1" id="${prefix}-price" value="${bus.pricePerSeat || ''}" placeholder="350">
            </div>
            <div class="form-group">
              <label class="form-label">Total Seats</label>
              <input class="form-input" type="number" min="1" step="1" id="${prefix}-seats" value="${bus.totalSeats || ''}" placeholder="40">
            </div>
          </div>
          <div class="admin-bus-actions">${actionButtons}</div>
        </div>
      </div>`;
  }

  async function createBusAdmin() {
    try {
      const payload = _adminBusPayload('admin-add');
      const error = _validateAdminBusPayload(payload);
      if (error) { UI.toast(error, 'error'); return; }
      await API.createAdminBus(payload);
      UI.toast('Bus added successfully.', 'success');
      await _loadCities();
      renderAdmin();
    } catch (err) {
      UI.toast(err.message, 'error');
    }
  }

  function startBusEdit(id) {
    _adminEditingBusId = id;
    renderAdmin();
  }

  function cancelBusEdit() {
    _adminEditingBusId = null;
    renderAdmin();
  }

  async function updateBusAdmin(id) {
    try {
      const payload = _adminBusPayload('admin-edit');
      const error = _validateAdminBusPayload(payload);
      if (error) { UI.toast(error, 'error'); return; }
      await API.updateAdminBus(id, payload);
      _adminEditingBusId = null;
      UI.toast('Bus details updated.', 'success');
      await _loadCities();
      renderAdmin();
    } catch (err) {
      UI.toast(err.message, 'error');
    }
  }

  async function deleteBusAdmin(id) {
    if (!confirm('Delete this bus? This will fail if bookings exist for it.')) return;
    try {
      await API.deleteAdminBus(id);
      if (_adminEditingBusId === id) _adminEditingBusId = null;
      UI.toast('Bus deleted successfully.', 'info');
      await _loadCities();
      renderAdmin();
    } catch (err) {
      UI.toast(err.message, 'error');
    }
  }

  function _adminSeatMonitorHTML(buses) {
    if (!buses.length) {
      return `
        <div class="table-card">
          <div class="table-header">
            <div class="table-title">Seat Occupancy</div>
            <div class="table-count">No buses available</div>
          </div>
          ${UI.emptyState('N/A', 'Add buses to monitor seat occupancy')}
        </div>`;
    }

    if (!_adminSeatBusId || !buses.some(b => b.id === _adminSeatBusId)) {
      _adminSeatBusId = buses[0].id;
    }

    return `
      <div class="table-card">
        <div class="table-header">
          <div class="table-title">Seat Occupancy</div>
          <div class="table-count">View booked seats by bus and date</div>
        </div>
        <div class="admin-bus-form">
          <div class="admin-seat-tools">
            <div class="form-group">
              <label class="form-label">Bus</label>
              <select class="form-select" id="admin-seat-bus">
                ${buses.map(b => `<option value="${b.id}" ${b.id === _adminSeatBusId ? 'selected' : ''}>${b.busNumber} | ${b.fromCity} -> ${b.toCity}</option>`).join('')}
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Travel Date</label>
              <input class="form-input" type="date" id="admin-seat-date" value="${_adminSeatDate}">
            </div>
            <div class="admin-bus-actions">
              <button class="btn btn-primary btn-sm" onclick="App.refreshAdminSeatView(true)">Refresh Seats</button>
            </div>
          </div>
          <div id="admin-seat-result" style="margin-top:14px;color:var(--text2)">Loading seat occupancy...</div>
        </div>
      </div>`;
  }

  async function refreshAdminSeatView(showToast = false) {
    const busEl = document.getElementById('admin-seat-bus');
    const dateEl = document.getElementById('admin-seat-date');
    const resultEl = document.getElementById('admin-seat-result');
    if (!busEl || !dateEl || !resultEl) return;

    _adminSeatBusId = Number(busEl.value);
    _adminSeatDate = dateEl.value || new Date().toISOString().split('T')[0];

    resultEl.textContent = 'Loading seat occupancy...';
    try {
      const seatData = await API.getSeats(_adminSeatBusId, _adminSeatDate);
      const all = (seatData.all || []).slice().sort((a, b) => a - b);
      const booked = (seatData.booked || []).slice().sort((a, b) => a - b);
      const availableCount = Math.max(all.length - booked.length, 0);

      resultEl.innerHTML = `
        <div style="display:flex;gap:18px;flex-wrap:wrap;margin-bottom:10px">
          <span><strong style="color:var(--text)">${all.length}</strong> total seats</span>
          <span><strong style="color:var(--accent2)">${booked.length}</strong> booked</span>
          <span><strong style="color:var(--accent3)">${availableCount}</strong> available</span>
        </div>
        <div style="font-size:13px">
          <strong style="color:var(--text)">Booked Seats:</strong>
          ${booked.length ? booked.join(', ') : 'None'}
        </div>`;

      if (showToast) UI.toast('Seat occupancy updated.', 'success');
    } catch (err) {
      resultEl.innerHTML = `<span style="color:var(--accent2)">Failed to fetch seats: ${err.message}</span>`;
      if (showToast) UI.toast(err.message, 'error');
    }
  }

  async function renderAdmin() {
    if (!_currentUser || _currentUser.role !== 'ADMIN') {
      UI.showPage('home'); return;
    }
    const container = document.getElementById('admin-content');
    container.innerHTML = UI.loadingHTML();

    try {
      const [stats, bookings, buses, users] = await Promise.all([
        API.getAdminStats(),
        API.getAllBookings(),
        API.getAdminBuses(),
        API.getAllUsers()
      ]);

      const editingBus = buses.find(b => b.id === _adminEditingBusId);

      container.innerHTML = `
        <div class="admin-stat-grid">
          <div class="admin-stat-card" style="--card-accent:#6c63ff">
            <div class="admin-stat-icon">BK</div>
            <div class="admin-stat-num">${stats.totalBookings}</div>
            <div class="admin-stat-lbl">Total Bookings</div>
          </div>
          <div class="admin-stat-card" style="--card-accent:#43e97b">
            <div class="admin-stat-icon">RS</div>
            <div class="admin-stat-num">Rs. ${Number(stats.totalRevenue || 0).toLocaleString('en-IN')}</div>
            <div class="admin-stat-lbl">Total Revenue</div>
          </div>
          <div class="admin-stat-card" style="--card-accent:#ff6b6b">
            <div class="admin-stat-icon">BUS</div>
            <div class="admin-stat-num">${stats.totalBuses}</div>
            <div class="admin-stat-lbl">Active Buses</div>
          </div>
          <div class="admin-stat-card" style="--card-accent:#ffd700">
            <div class="admin-stat-icon">USR</div>
            <div class="admin-stat-num">${stats.totalUsers}</div>
            <div class="admin-stat-lbl">Registered Users</div>
          </div>
        </div>

        <div class="table-card">
          <div class="table-header">
            <div class="table-title">All Bookings</div>
            <div class="table-count">${bookings.length} total</div>
          </div>
          ${bookings.length === 0
            ? UI.emptyState('N/A', 'No bookings yet')
            : `<div style="overflow-x:auto"><table>
                <thead><tr>
                  <th>Booking Ref</th><th>Passenger</th><th>Route</th>
                  <th>Date</th><th>Seats</th><th>Amount</th><th>Status</th>
                </tr></thead>
                <tbody>
                  ${bookings.map(b => `<tr>
                    <td style="font-weight:600;color:var(--accent)">${b.bookingRef || b.id}</td>
                    <td>${b.passengerName}<div class="sub">${b.passengerPhone || ''}</div></td>
                    <td>${b.bus?.fromCity} -> ${b.bus?.toCity}<div class="sub">${b.bus?.operatorName}</div></td>
                    <td>${UI.formatDate(b.travelDate)}<div class="sub">${b.bus?.departureTime}</div></td>
                    <td>${b.seatNumbers}</td>
                    <td style="font-weight:600;color:var(--accent3)">Rs. ${b.totalAmount}</td>
                    <td><span class="badge ${b.status === 'CONFIRMED' ? 'badge-success' : 'badge-danger'}">${b.status}</span></td>
                  </tr>`).join('')}
                </tbody>
              </table></div>`
          }
        </div>

        ${_adminBusFormHTML(editingBus ? 'edit' : 'create', editingBus || {})}
        ${_adminSeatMonitorHTML(buses)}

        <div class="table-card">
          <div class="table-header">
            <div class="table-title">Bus Fleet</div>
            <div class="table-count">${buses.length} buses</div>
          </div>
          <div style="overflow-x:auto"><table>
            <thead><tr>
              <th>Bus No.</th><th>Operator</th><th>Route</th><th>Type</th>
              <th>Departure</th><th>Arrival</th><th>Price</th><th>Capacity</th><th>Actions</th>
            </tr></thead>
            <tbody>
              ${buses.map(b => `<tr>
                <td style="font-weight:600">${b.busNumber}</td>
                <td>${b.operatorName}</td>
                <td>${b.fromCity} -> ${b.toCity}</td>
                <td><span class="badge badge-warning">${b.busType}</span></td>
                <td>${b.departureTime}</td>
                <td>${b.arrivalTime}</td>
                <td>Rs. ${b.pricePerSeat}</td>
                <td>${b.totalSeats} seats</td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="App.startBusEdit(${b.id})">Edit</button>
                  <button class="btn btn-danger btn-sm" onclick="App.deleteBusAdmin(${b.id})">Delete</button>
                </td>
              </tr>`).join('')}
            </tbody>
          </table></div>
        </div>

        <div class="table-card">
          <div class="table-header">
            <div class="table-title">Registered Users</div>
            <div class="table-count">${users.length} users</div>
          </div>
          <div style="overflow-x:auto"><table>
            <thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Role</th></tr></thead>
            <tbody>
              ${users.map(u => `<tr>
                <td style="color:var(--text3)">#${u.id}</td>
                <td>${u.name}</td>
                <td style="color:var(--text2)">${u.email}</td>
                <td><span class="badge ${u.role === 'ADMIN' ? 'badge-info' : 'badge-success'}">${u.role}</span></td>
              </tr>`).join('')}
            </tbody>
          </table></div>
        </div>`;

      refreshAdminSeatView(false);

    } catch (err) {
      container.innerHTML = UI.emptyState('ERR', 'Failed to load dashboard', err.message);
    }
  }

  // ----------------------------------------------------------------
  //  Public
  // ----------------------------------------------------------------

  return {
    init, getCurrentUser,
    handleLogin, handleRegister, logout, requireAuth,
    searchBuses, clearSearch, selectBus, selectBusEncoded,
    renderBookings, cancelBooking,
    renderAdmin, createBusAdmin, startBusEdit, cancelBusEdit, updateBusAdmin, deleteBusAdmin,
    refreshAdminSeatView
  };

})();

// ---- Confirmation module (inline — small enough) ----
const Confirmation = (() => {
  function show(booking) {
    const container = document.getElementById('confirmation-content');
    const seed = booking.id || Date.now();

    container.innerHTML = `
      <div class="confirmation-icon">✓</div>
      <h1 style="font-family:'Instrument Serif',serif;font-size:36px;font-style:italic;margin-bottom:8px">
        Booking Confirmed!
      </h1>
      <p style="color:var(--text2);margin-bottom:8px">
        Your ticket is reserved. Have a great journey!
      </p>

      <div class="ticket-card">
        <div class="ticket-header">
          <div style="font-size:13px;opacity:0.8;margin-bottom:6px">🚌 ${booking.bus.operatorName}</div>
          <div class="ticket-route">${booking.bus.fromCity} → ${booking.bus.toCity}</div>
          <div class="ticket-meta">${UI.formatDate(booking.travelDate)} · ${booking.bus.departureTime} – ${booking.bus.arrivalTime}</div>
        </div>
        <div class="ticket-divider">
          <div class="ticket-notch"></div>
          <div class="ticket-perf"></div>
          <div class="ticket-notch"></div>
        </div>
        <div class="ticket-body">
          <div class="ticket-grid">
            <div><div class="tf-label">Passenger</div>  <div class="tf-value">${booking.passengerName}</div></div>
            <div><div class="tf-label">Seat(s)</div>    <div class="tf-value">${booking.seatNumbers}</div></div>
            <div><div class="tf-label">Age</div>        <div class="tf-value">${booking.passengerAge} yrs</div></div>
            <div><div class="tf-label">Bus Type</div>   <div class="tf-value">${booking.bus.busType}</div></div>
            <div><div class="tf-label">Phone</div>      <div class="tf-value">${booking.passengerPhone}</div></div>
            <div><div class="tf-label">Payment</div>    <div class="tf-value">${booking.paymentStatus || 'PAID'} (${booking.paymentMethod || 'N/A'})</div></div>
            <div><div class="tf-label">Txn Ref</div>    <div class="tf-value">${booking.paymentReference || 'N/A'}</div></div>
            <div>
              <div class="tf-label">Total Fare</div>
              <div class="tf-value" style="color:var(--accent3)">₹${booking.totalAmount}</div>
            </div>
          </div>
        </div>
        <div class="ticket-footer">
          <div>
            <div style="font-size:11px;color:var(--text3)">Booking Reference</div>
            <div style="font-weight:700;font-size:15px">${booking.bookingRef}</div>
          </div>
          <div class="barcode">${UI.barcodeHTML(seed)}</div>
        </div>
      </div>

      <div style="display:flex;gap:16px;justify-content:center;flex-wrap:wrap">
        <button class="btn btn-primary btn-lg" onclick="App.requireAuth('bookings')">View All Bookings</button>
        <button class="btn btn-secondary btn-lg" onclick="UI.showPage('search')">Book Another</button>
      </div>`;
  }

  return { show };
})();

// ---- Bootstrap ----
document.addEventListener('DOMContentLoaded', App.init);


