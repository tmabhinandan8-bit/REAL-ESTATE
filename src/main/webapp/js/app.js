/**
 * REAL ESTATE MANAGEMENT SYSTEM - CORE JAVASCRIPT APPLICATION
 * White, Green & Black Frontend connected to Java Backend + Supabase
 * Includes Live Email Validator, Auth State, Compare & Toast Notifications
 */

const App = {
  currentUser: null,
  compareList: JSON.parse(localStorage.getItem('realestate_compare') || '[]'),

  init() {
    this.checkAuth();
    this.initCompareBadge();
  },

  // =========================================================================
  // AUTHENTICATION STATE
  // =========================================================================
  async checkAuth() {
    try {
      const res = await fetch('/api/auth/me');
      const data = await res.json();
      if (data.status === 'success' && data.data) {
        this.currentUser = data.data;
        this.renderAuthNav(true);
      } else {
        this.currentUser = null;
        this.renderAuthNav(false);
      }
    } catch (e) {
      this.currentUser = null;
      this.renderAuthNav(false);
    }
  },

  renderAuthNav(isLoggedIn) {
    const authContainer = document.getElementById('nav-auth-container');
    if (!authContainer) return;

    if (isLoggedIn && this.currentUser) {
      const isAdmin = this.currentUser.role === 'ADMIN';
      authContainer.innerHTML = `
        <div class="dropdown">
          <button class="btn btn-black dropdown-toggle btn-sm py-2 px-3 d-flex align-items-center gap-2" type="button" data-bs-toggle="dropdown">
            <i class="bi bi-person-circle text-success"></i>
            <span>${this.escapeHtml(this.currentUser.fullName || 'User')}</span>
            <span class="badge bg-success text-white text-uppercase ms-1" style="font-size:0.68rem">${this.escapeHtml(this.currentUser.role)}</span>
          </button>
          <ul class="dropdown-menu dropdown-menu-end shadow border-0">
            <li><a class="dropdown-item py-2" href="dashboard.html"><i class="bi bi-speedometer2 me-2 text-success"></i>My Dashboard</a></li>
            <li><a class="dropdown-item py-2" href="post-property.html"><i class="bi bi-plus-circle me-2 text-success"></i>Post Property</a></li>
            ${isAdmin ? '<li><a class="dropdown-item py-2 text-success fw-bold" href="admin.html"><i class="bi bi-shield-check me-2"></i>Admin Portal</a></li>' : ''}
            <li><hr class="dropdown-divider"></li>
            <li><a class="dropdown-item py-2 text-danger" href="#" onclick="App.logout(event)"><i class="bi bi-box-arrow-right me-2"></i>Logout</a></li>
          </ul>
        </div>
      `;
    } else {
      authContainer.innerHTML = `
        <div class="d-flex align-items-center gap-2">
          <a href="login.html" class="btn btn-outline-custom btn-sm">Login</a>
          <a href="register.html" class="btn btn-primary-custom btn-sm">Register</a>
        </div>
      `;
    }
  },

  async logout(e) {
    if (e) e.preventDefault();
    try {
      await fetch('/api/auth/logout', { method: 'POST' });
    } catch (err) {}
    localStorage.removeItem('realestate_user');
    window.location.href = 'index.html';
  },

  // =========================================================================
  // LIVE EMAIL VALIDATOR (Client Syntax + Server DNS MX Check)
  // =========================================================================
  isValidEmailFormat(email) {
    if (!email) return false;
    const trimmed = email.trim();
    if (trimmed.includes('..') || trimmed.length > 120) return false;
    return /^[A-Za-z0-9](?:[A-Za-z0-9._%+-]{0,62}[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\.[A-Za-z]{2,63}$/.test(trimmed);
  },

  async validateEmailServer(email, checkExists = false) {
    const body = new URLSearchParams({ email: email.trim(), checkExists: checkExists ? 'true' : 'false' });
    const res = await fetch('/api/auth/validate-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    });
    const json = await res.json();
    return json.data || { valid: false, message: json.message || 'Invalid email address' };
  },

  attachEmailValidator(inputId, feedbackId, options = {}) {
    const input = document.getElementById(inputId);
    const feedback = document.getElementById(feedbackId);
    if (!input || !feedback) return { isValid: () => false };

    let validState = false;
    let debounceTimer = null;

    const runCheck = async (immediate = false) => {
      const val = input.value.trim();
      if (!val) {
        validState = false;
        input.classList.remove('is-valid', 'is-invalid');
        feedback.className = 'email-feedback';
        feedback.innerHTML = '';
        return false;
      }

      if (!App.isValidEmailFormat(val)) {
        validState = false;
        input.classList.remove('is-valid');
        input.classList.add('is-invalid');
        feedback.className = 'email-feedback invalid';
        feedback.innerHTML = '<i class="bi bi-x-circle-fill"></i> Enter a valid email address (e.g. name@domain.com)';
        return false;
      }

      const verify = async () => {
        feedback.className = 'email-feedback checking';
        feedback.innerHTML = '<span class="spinner-border spinner-border-sm" style="width:0.75rem;height:0.75rem"></span> Verifying email domain...';
        try {
          const result = await App.validateEmailServer(val, !!options.checkExists);
          if (input.value.trim() !== val) return validState;

          if (!result.valid) {
            validState = false;
            input.classList.remove('is-valid');
            input.classList.add('is-invalid');
            feedback.className = 'email-feedback invalid';
            if (result.suggestion) {
              feedback.innerHTML = `<i class="bi bi-exclamation-circle-fill"></i> ${App.escapeHtml(result.message)} <button type="button" class="btn btn-link btn-sm p-0 ms-1 text-success fw-bold text-decoration-none" id="${feedbackId}-fix">Fix</button>`;
              const fixBtn = document.getElementById(`${feedbackId}-fix`);
              if (fixBtn) {
                fixBtn.onclick = () => {
                  input.value = result.suggestion;
                  runCheck(true);
                };
              }
            } else {
              feedback.innerHTML = `<i class="bi bi-x-circle-fill"></i> ${App.escapeHtml(result.message)}`;
            }
            return false;
          }

          if (options.requireNotRegistered && result.registered) {
            validState = false;
            input.classList.remove('is-valid');
            input.classList.add('is-invalid');
            feedback.className = 'email-feedback invalid';
            feedback.innerHTML = '<i class="bi bi-x-circle-fill"></i> This email is already registered. <a href="login.html" class="text-success fw-bold ms-1">Sign in</a>';
            return false;
          }

          if (options.requireRegistered && !result.registered) {
            validState = false;
            input.classList.remove('is-valid');
            input.classList.add('is-invalid');
            feedback.className = 'email-feedback invalid';
            feedback.innerHTML = '<i class="bi bi-x-circle-fill"></i> No account found with this email address.';
            return false;
          }

          validState = true;
          input.classList.remove('is-invalid');
          input.classList.add('is-valid');
          feedback.className = 'email-feedback valid';
          feedback.innerHTML = `<i class="bi bi-check-circle-fill"></i> ${App.escapeHtml(result.message)}`;
          return true;
        } catch (err) {
          validState = App.isValidEmailFormat(val);
          return validState;
        }
      };

      if (immediate) return verify();
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(verify, 420);
      return validState;
    };

    input.addEventListener('input', () => runCheck(false));
    input.addEventListener('blur', () => runCheck(true));

    return {
      validateNow: () => runCheck(true),
      isValid: () => validState
    };
  },

  // =========================================================================
  // CURRENCY, COMPARE, TOASTS & HTML ESCAPING
  // =========================================================================
  formatPrice(price, purpose) {
    if (!price || isNaN(price)) return 'Price on Request';
    const num = Number(price);
    const suffix = (purpose === 'RENT' || purpose === 'LEASE') ? ' / month' : '';

    if (num >= 10000000) {
      return '₹ ' + (num / 10000000).toFixed(2) + ' Cr' + suffix;
    } else if (num >= 100000) {
      return '₹ ' + (num / 100000).toFixed(2) + ' Lakh' + suffix;
    } else {
      return '₹ ' + num.toLocaleString('en-IN') + suffix;
    }
  },

  toggleCompare(propertyId, btnElement) {
    propertyId = parseInt(propertyId);
    const idx = this.compareList.indexOf(propertyId);
    if (idx > -1) {
      this.compareList.splice(idx, 1);
      this.showToast('Property removed from comparison');
    } else {
      if (this.compareList.length >= 3) {
        this.showToast('You can compare a maximum of 3 properties at a time.', 'warning');
        return;
      }
      this.compareList.push(propertyId);
      this.showToast('Property added to comparison list');
    }
    localStorage.setItem('realestate_compare', JSON.stringify(this.compareList));
    this.initCompareBadge();

    if (btnElement) {
      const inList = this.compareList.includes(propertyId);
      btnElement.classList.toggle('btn-primary-custom', inList);
      btnElement.classList.toggle('btn-outline-secondary', !inList);
      btnElement.innerHTML = inList ? '<i class="bi bi-check2"></i> Added to Compare' : '<i class="bi bi-arrow-left-right"></i> Compare';
    }
  },

  initCompareBadge() {
    const badge = document.getElementById('compare-count-badge');
    if (badge) {
      badge.textContent = this.compareList.length;
      badge.style.display = this.compareList.length > 0 ? 'inline-block' : 'none';
    }
    const compareLink = document.getElementById('nav-compare-link');
    if (compareLink && this.compareList.length > 0) {
      compareLink.href = 'compare.html?ids=' + this.compareList.join(',');
    }
  },

  showToast(message, type = 'success') {
    let container = document.getElementById('custom-toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'custom-toast-container';
      container.className = 'toast-container-custom';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    const bgClass = type === 'success' ? 'bg-success' : (type === 'warning' ? 'bg-dark border border-success' : 'bg-danger');
    toast.className = `toast align-items-center text-white ${bgClass} border-0 show shadow-lg mb-2`;
    toast.role = 'alert';
    toast.innerHTML = `
      <div class="d-flex">
        <div class="toast-body d-flex align-items-center gap-2 py-3 px-3">
          <i class="bi ${type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'} fs-5"></i>
          <span>${this.escapeHtml(message)}</span>
        </div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto" onclick="this.parentElement.parentElement.remove()"></button>
      </div>
    `;
    container.appendChild(toast);
    setTimeout(() => {
      toast.classList.remove('show');
      setTimeout(() => toast.remove(), 300);
    }, 4500);
  },

  escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
};

document.addEventListener('DOMContentLoaded', () => App.init());
