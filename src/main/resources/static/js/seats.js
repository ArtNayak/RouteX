/**
 * seats.js - Seat selection + payment module.
 * Renders the bus seat map, manages selection, and completes booking after payment details.
 */

const Seats = (() => {

  let _bus = null;
  let _date = null;
  let _selected = [];
  let _bookedSeats = [];

  async function render(bus, date) {
    _bus = bus;
    _date = date;
    _selected = [];

    const container = document.getElementById('seat-page-content');
    container.innerHTML = UI.loadingHTML();

    try {
      const seatData = await API.getSeats(bus.id, date);
      _bookedSeats = seatData.booked || [];
      _renderLayout(container);
    } catch (err) {
      container.innerHTML = UI.emptyState('X', 'Failed to load seats', err.message);
    }
  }

  function _renderLayout(container) {
    const ROWS = 10;
    let rowsHTML = '';

    for (let r = 0; r < ROWS; r++) {
      const [s1, s2, s3, s4] = [r * 4 + 1, r * 4 + 2, r * 4 + 3, r * 4 + 4];
      rowsHTML += `<div class="seat-row">
        ${_seatEl(s1)} ${_seatEl(s2)}
        <div class="aisle-gap"></div>
        ${_seatEl(s3)} ${_seatEl(s4)}
      </div>`;
    }

    container.innerHTML = `
      <div style="margin-bottom:20px">
        <button class="btn btn-secondary btn-sm" onclick="UI.showPage('search')"><- Back to Results</button>
      </div>

      <div class="page-title">${_bus.fromCity} -> ${_bus.toCity}</div>
      <div class="page-sub">${_bus.operatorName} | ${_bus.busType} | ${UI.formatDate(_date)}</div>

      <div class="seat-booking-layout">
        <div class="card">
          <h3 style="font-size:16px;font-weight:600;margin-bottom:24px">Pick your seats</h3>
          <div class="bus-3d-wrapper">
            <div class="bus-3d-body">
              <div class="bus-driver-row">
                <div class="steering-icon">DRV</div>
                <div>Driver Section</div>
              </div>
              <div id="seat-map">${rowsHTML}</div>
            </div>
          </div>
          <div class="seat-legend">
            <div class="legend-item"><div class="legend-dot" style="background:rgba(67,233,123,0.2);border:1.5px solid rgba(67,233,123,0.4)"></div>Available</div>
            <div class="legend-item"><div class="legend-dot" style="background:rgba(108,99,255,0.25);border:1.5px solid #6c63ff"></div>Selected</div>
            <div class="legend-item"><div class="legend-dot" style="background:rgba(255,107,107,0.08);border:1.5px solid rgba(255,107,107,0.2)"></div>Booked</div>
          </div>
        </div>

        <div class="booking-summary">
          <div class="summary-title">Booking Summary</div>

          <div class="summary-row"><span class="lbl">Route</span><span class="val">${_bus.fromCity} -> ${_bus.toCity}</span></div>
          <div class="summary-row"><span class="lbl">Operator</span><span class="val">${_bus.operatorName}</span></div>
          <div class="summary-row"><span class="lbl">Date</span><span class="val">${UI.formatDate(_date)}</span></div>
          <div class="summary-row"><span class="lbl">Departs</span><span class="val">${_bus.departureTime}</span></div>
          <div class="summary-row"><span class="lbl">Seats</span><span class="val" id="sum-seats">- None selected -</span></div>
          <div class="summary-row"><span class="lbl">Per Seat</span><span class="val">Rs. ${_bus.pricePerSeat}</span></div>

          <div class="summary-total">
            <span>Total</span>
            <span class="amount" id="sum-total">Rs. 0</span>
          </div>

          <div class="passenger-form">
            <div class="form-group">
              <label class="form-label">Passenger Name</label>
              <input class="form-input" type="text" id="p-name" placeholder="Full name">
            </div>
            <div class="form-group">
              <label class="form-label">Phone Number</label>
              <input class="form-input" type="tel" id="p-phone" placeholder="10-digit mobile">
            </div>
            <div class="form-group">
              <label class="form-label">Age</label>
              <input class="form-input" type="number" id="p-age" placeholder="Your age" min="1" max="120">
            </div>
          </div>

          <div class="passenger-form" style="margin-top:16px;padding-top:16px;border-top:1px solid var(--border)">
            <div class="form-group">
              <label class="form-label">Payment Method</label>
              <select class="form-select" id="pay-method" onchange="Seats.onPaymentMethodChange()">
                <option value="CARD">Card</option>
                <option value="UPI">UPI</option>
                <option value="NET_BANKING">Net Banking</option>
                <option value="WALLET">Wallet</option>
              </select>
            </div>
            <div class="form-group" id="pay-detail-group">
              <label class="form-label" id="pay-detail-label">Card Number</label>
              <input class="form-input" type="text" id="pay-detail" placeholder="XXXX XXXX XXXX 1234">
            </div>
          </div>

          <button class="btn btn-primary btn-full" style="margin-top:16px" id="book-btn" onclick="Seats.confirmBooking()" disabled>
            Select seats to proceed
          </button>
        </div>
      </div>`;

    const user = App.getCurrentUser();
    if (user) document.getElementById('p-name').value = user.name || '';
    onPaymentMethodChange();
  }

  function _seatEl(num) {
    if (_bookedSeats.includes(num)) {
      return `<div class="seat booked" title="Seat ${num} - Booked">${num}</div>`;
    }
    return `<div class="seat available" id="seat-${num}" onclick="Seats.toggle(${num})" title="Seat ${num}">${num}</div>`;
  }

  function toggle(num) {
    const idx = _selected.indexOf(num);
    if (idx >= 0) {
      _selected.splice(idx, 1);
      document.getElementById('seat-' + num).className = 'seat available';
    } else {
      if (_selected.length >= 6) {
        UI.toast('Maximum 6 seats per booking', 'error');
        return;
      }
      _selected.push(num);
      document.getElementById('seat-' + num).className = 'seat selected';
    }
    _updateSummary();
  }

  function _updateSummary() {
    const seatsEl = document.getElementById('sum-seats');
    const totalEl = document.getElementById('sum-total');
    const bookBtn = document.getElementById('book-btn');

    if (_selected.length === 0) {
      seatsEl.textContent = '- None selected -';
      totalEl.textContent = 'Rs. 0';
      bookBtn.disabled = true;
      bookBtn.textContent = 'Select seats to proceed';
    } else {
      seatsEl.textContent = _selected.slice().sort((a, b) => a - b).join(', ');
      totalEl.textContent = `Rs. ${_selected.length * _bus.pricePerSeat}`;
      bookBtn.disabled = false;
      bookBtn.textContent = `Pay & Book ${_selected.length} Seat${_selected.length > 1 ? 's' : ''}`;
    }
  }

  function onPaymentMethodChange() {
    const methodEl = document.getElementById('pay-method');
    const labelEl = document.getElementById('pay-detail-label');
    const detailEl = document.getElementById('pay-detail');
    if (!methodEl || !labelEl || !detailEl) return;

    const method = methodEl.value;
    if (method === 'UPI') {
      labelEl.textContent = 'UPI ID';
      detailEl.placeholder = 'name@bank';
    } else if (method === 'NET_BANKING') {
      labelEl.textContent = 'Bank Reference';
      detailEl.placeholder = 'Bank txn reference';
    } else if (method === 'WALLET') {
      labelEl.textContent = 'Wallet Number';
      detailEl.placeholder = 'Wallet mobile/email';
    } else {
      labelEl.textContent = 'Card Number';
      detailEl.placeholder = 'XXXX XXXX XXXX 1234';
    }
  }

  function _generatePaymentReference(method, detail) {
    const seed = Date.now().toString().slice(-8);
    const suffix = (detail || 'PAY').replace(/\s+/g, '').slice(-4).toUpperCase();
    return `${method}-${seed}-${suffix || '0000'}`;
  }

  async function confirmBooking() {
    const name = document.getElementById('p-name').value.trim();
    const phone = document.getElementById('p-phone').value.trim();
    const age = parseInt(document.getElementById('p-age').value.trim(), 10);
    const paymentMethod = document.getElementById('pay-method').value;
    const paymentDetail = document.getElementById('pay-detail').value.trim();

    if (!name) { UI.toast('Enter passenger name', 'error'); return; }
    if (!/^\d{10}$/.test(phone)) { UI.toast('Enter a valid 10-digit phone', 'error'); return; }
    if (!age || age < 1) { UI.toast('Enter a valid age', 'error'); return; }
    if (_selected.length < 1) { UI.toast('Select at least one seat', 'error'); return; }
    if (!paymentDetail) { UI.toast('Enter payment details', 'error'); return; }

    const paymentReference = _generatePaymentReference(paymentMethod, paymentDetail);
    const btn = document.getElementById('book-btn');
    btn.disabled = true;
    btn.textContent = 'Processing payment...';

    try {
      await new Promise(resolve => setTimeout(resolve, 700));
      const booking = await API.createBooking({
        busId: _bus.id,
        travelDate: _date,
        seats: [..._selected],
        passengerName: name,
        passengerPhone: phone,
        passengerAge: age,
        paymentMethod,
        paymentReference
      });
      Confirmation.show(booking);
      UI.showPage('confirmation');
      UI.toast('Payment successful. Booking confirmed!', 'success');
    } catch (err) {
      UI.toast(err.message, 'error');
      btn.disabled = false;
      btn.textContent = `Pay & Book ${_selected.length} Seat${_selected.length > 1 ? 's' : ''}`;
    }
  }

  return { render, toggle, confirmBooking, onPaymentMethodChange };

})();
