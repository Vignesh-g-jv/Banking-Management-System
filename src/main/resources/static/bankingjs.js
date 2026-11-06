'use strict';

// =====================================================
// API CONFIGURATION
// =====================================================

// Backend URL used when the page is NOT served by Spring Boot itself.
// Change this to your deployed backend if hosted separately, e.g.
// const BACKEND_URL = 'https://your-banking-backend.onrender.com';
const BACKEND_URL = 'https://banking-management-system-g2gh.onrender.com';

// Ports commonly used by static dev servers (Live Server, Vite, etc.).
// If the page is opened from one of these (or from file://), requests
// are sent to BACKEND_URL instead of the static server, which would
// answer POST requests with "405 Method Not Allowed".
const STATIC_DEV_PORTS = ['3000', '4200', '5173', '5500', '5501', '8000', '8081'];

const servedByStaticServer =
    window.location.protocol === 'file:' ||
    STATIC_DEV_PORTS.includes(window.location.port);

// Empty string = same origin (frontend and backend in the same Spring Boot app).
const API_BASE_URL = BACKEND_URL;

const API_PREFIX = '/api';

const money = new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR'
});

const dateOnly = new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium'
});

const timeOnly = new Intl.DateTimeFormat('en-IN', {
    timeStyle: 'short'
});

const $ = (selector) => document.querySelector(selector);

const state = {
    accounts: [],
    selected: null
};

// =====================================================
// HELPER FUNCTIONS
// =====================================================

function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[c]);
}

function typeLabel(type) {
    return type === 'SAVINGS'
        ? 'Savings'
        : type === 'CURRENT'
            ? 'Current'
            : type;
}

function errorText(body, status) {
    if (body && body.fieldErrors) {
        return Object.values(body.fieldErrors).join('. ');
    }

    if (body) {
        return body.message ||
            body.detail ||
            body.title ||
            `Request failed (${status})`;
    }

    return `Request failed (${status})`;
}

// =====================================================
// API REQUEST FUNCTION
// =====================================================

async function api(path, options = {}) {

    const url = `${API_BASE_URL.replace(/\/$/, '')}${API_PREFIX}${path}`;

    const method = options.method || 'GET';

    const headers = {
        'Accept': 'application/json',
        ...(options.headers || {})
    };

    // Only add JSON Content-Type when sending a request body.
    if (options.body !== undefined && options.body !== null) {
        headers['Content-Type'] = 'application/json';
    }

    let response;

    try {
        response = await fetch(url, {
            ...options,
            method,
            headers
        });
    } catch (error) {
        throw new Error(
            `Cannot connect to the backend at ${url}. ` +
            `Check the backend URL, that the server is running, and CORS settings.`
        );
    }

    const responseText = await response.text();

    let body = null;

    try {
        body = responseText ? JSON.parse(responseText) : null;
    } catch (error) {
        body = null;
    }

    if (!response.ok) {

        if (response.status === 405) {
            const allowed = response.headers.get('Allow');

            throw new Error(
                `405 Method Not Allowed for ${method} ${url}. ` +
                (allowed
                    ? `Server allows: ${allowed}. `
                    : 'The page may be served by a static server instead of Spring Boot. ') +
                `Check the controller mapping or set BACKEND_URL in the JS file.`
            );
        }

        if (response.status === 404) {
            throw new Error(
                `404 API endpoint not found: ${url}`
            );
        }

        if (response.status === 403) {
            throw new Error(
                '403 Forbidden. Check backend security and CORS configuration.'
            );
        }

        throw new Error(errorText(body, response.status));
    }

    return body;
}

// =====================================================
// TOAST NOTIFICATION
// =====================================================

let toastTimer = null;

function toast(message, isError = false) {

    const el = $('#toast');

    el.textContent = message;

    el.classList.toggle('error', isError);

    el.hidden = false;

    clearTimeout(toastTimer);

    toastTimer = setTimeout(() => {
        el.hidden = true;
    }, isError ? 7000 : 3500);
}

// =====================================================
// FORM HELPERS
// =====================================================

function jsonBody(form, numericFields = []) {

    const data = Object.fromEntries(
        new FormData(form).entries()
    );

    for (const key of Object.keys(data)) {

        if (typeof data[key] === 'string') {
            data[key] = data[key].trim();
        }

        if (data[key] === '') {
            delete data[key];
        }
    }

    for (const key of numericFields) {

        if (key in data) {
            data[key] = Number(data[key]);
        }
    }

    return JSON.stringify(data);
}

async function withBusy(form, work) {

    const button = form.querySelector('button[type="submit"]');

    button.disabled = true;

    try {
        await work();
    } catch (error) {
        toast(error.message, true);
    } finally {
        button.disabled = false;
    }
}

// =====================================================
// ACCOUNT MANAGEMENT
// =====================================================

async function loadAccounts() {

    const result = await api('/accounts');

    state.accounts = Array.isArray(result) ? result : [];

    renderAccounts();
}

function renderAccounts() {

    const active = state.accounts.filter(
        account => account.status === 'ACTIVE'
    );

    const total = active.reduce(
        (sum, account) => sum + Number(account.balance || 0),
        0
    );

    $('#stat-accounts').textContent = String(active.length);

    $('#stat-total').textContent = money.format(total);

    $('#accounts-empty').hidden = state.accounts.length > 0;

    $('#accounts-body').innerHTML = state.accounts.map(account => `

        <tr class="${account.accountNumber === state.selected ? 'selected' : ''}">

            <td>
                <span class="acct-no">
                    ${esc(account.accountNumber)}
                </span>
            </td>

            <td>
                ${esc(account.holderName)}
                <br>
                <span class="muted">
                    ${esc(account.email)}
                </span>
            </td>

            <td>
                ${esc(typeLabel(account.accountType))}
                ${account.status === 'CLOSED'
                    ? '<br><span class="tag-closed">Closed</span>'
                    : ''}
            </td>

            <td class="num">
                ${money.format(Number(account.balance || 0))}
            </td>

            <td class="num">
                <button
                    type="button"
                    class="link"
                    data-open="${esc(account.accountNumber)}">
                    Open passbook
                </button>
            </td>

        </tr>

    `).join('');

    // Transfer source accounts

    const options = active.map(account => `
        <option value="${esc(account.accountNumber)}">
            ${esc(account.accountNumber)} - ${esc(account.holderName)}
        </option>
    `);

    const from = $('#transfer-from');

    const previous = from.value;

    from.innerHTML = options.length
        ? options.join('')
        : '<option value="">No active accounts</option>';

    if (previous && active.some(a => a.accountNumber === previous)) {
        from.value = previous;
    } else if (
        state.selected &&
        active.some(a => a.accountNumber === state.selected)
    ) {
        from.value = state.selected;
    }

    $('#account-options').innerHTML = active.map(account => `
        <option value="${esc(account.accountNumber)}">
            ${esc(account.holderName)}
        </option>
    `).join('');
}

// =====================================================
// PASSBOOK MANAGEMENT
// =====================================================

async function openPassbook(accountNumber, { flash = false } = {}) {

    state.selected = accountNumber;

    const filter = $('#filter-type').value;

    const query = filter
        ? `?type=${encodeURIComponent(filter)}`
        : '';

    const number = encodeURIComponent(accountNumber);

    try {

        const [account, rows] = await Promise.all([

            api(`/accounts/${number}`),

            api(`/accounts/${number}/transactions${query}`)

        ]);

        renderPassbook(account, rows, flash);

        renderAccounts();

    } catch (error) {

        toast(error.message, true);

    }
}

function renderPassbook(account, rows, flash) {

    $('#passbook-empty').hidden = true;

    $('#passbook-body').hidden = false;

    $('#pb-number').textContent = account.accountNumber;

    $('#pb-holder').textContent =
        `${account.holderName}, ${typeLabel(account.accountType)} account` +
        (account.status === 'CLOSED' ? ' (closed)' : '');

    const balance = $('#pb-balance');

    balance.textContent = money.format(Number(account.balance));

    balance.classList.remove('flash');

    if (flash) {
        void balance.offsetWidth;
        balance.classList.add('flash');
    }

    const closed = account.status === 'CLOSED';

    for (const id of ['#deposit-form', '#withdraw-form']) {

        for (const control of $(id).querySelectorAll('input, button')) {
            control.disabled = closed;
        }
    }

    $('#close-account').disabled = closed;

    $('#ledger-empty').hidden = rows.length > 0;

    $('#ledger-body').innerHTML = rows.map(transaction => {

        const debit =
            transaction.type === 'WITHDRAWAL' ||
            transaction.type === 'TRANSFER_OUT';

        const amount = money.format(Number(transaction.amount));

        const counterparty = transaction.counterpartyAccount
            ? `<small>
                ${transaction.type === 'TRANSFER_OUT' ? 'To' : 'From'}
                account ${esc(transaction.counterpartyAccount)}
               </small>`
            : '';

        return `

            <tr>

                <td class="when">
                    ${esc(dateOnly.format(new Date(transaction.createdAt)))}
                    <small>
                        ${esc(timeOnly.format(new Date(transaction.createdAt)))}
                    </small>
                </td>

                <td class="details">
                    ${esc(transaction.description || '')}
                    ${counterparty}
                </td>

                <td class="num debit-cell">
                    ${debit ? amount : ''}
                </td>

                <td class="num credit-cell">
                    ${debit ? '' : amount}
                </td>

                <td class="num">
                    ${money.format(Number(transaction.balanceAfter))}
                </td>

            </tr>

        `;

    }).join('');
}

// =====================================================
// REFRESH DATA
// =====================================================

async function refreshAfterChange() {

    await loadAccounts();

    if (state.selected) {
        await openPassbook(state.selected, { flash: true });
    }
}

// =====================================================
// OPEN ACCOUNT
// =====================================================

$('#open-form').addEventListener('submit', event => {

    event.preventDefault();

    const form = event.currentTarget;

    withBusy(form, async () => {

        const created = await api('/accounts', {

            method: 'POST',

            body: jsonBody(form, ['initialDeposit'])

        });

        form.reset();

        toast(
            `Opened account ${created.accountNumber} for ${created.holderName}`
        );

        state.selected = created.accountNumber;

        await loadAccounts();

        await openPassbook(created.accountNumber);

    });

});

// =====================================================
// DEPOSIT AND WITHDRAWAL
// =====================================================

function moneyForm(selector, action, doneText) {

    $(selector).addEventListener('submit', event => {

        event.preventDefault();

        const form = event.currentTarget;

        if (!state.selected) {
            toast('Please select an account first.', true);
            return;
        }

        withBusy(form, async () => {

            const number = encodeURIComponent(state.selected);

            const tx = await api(
                `/accounts/${number}/${action}`,
                {
                    method: 'POST',
                    body: jsonBody(form, ['amount'])
                }
            );

            form.reset();

            toast(
                `${doneText} ${money.format(Number(tx.amount))}`
            );

            await refreshAfterChange();

        });

    });

}

moneyForm('#deposit-form', 'deposit', 'Deposited');

moneyForm('#withdraw-form', 'withdraw', 'Withdrew');

// =====================================================
// TRANSFER FUNDS
// =====================================================

$('#transfer-form').addEventListener('submit', event => {

    event.preventDefault();

    const form = event.currentTarget;

    withBusy(form, async () => {

        const result = await api('/transfers', {

            method: 'POST',

            body: jsonBody(form, ['amount'])

        });

        $('#transfer-to').value = '';

        $('#transfer-amount').value = '';

        $('#transfer-note').value = '';

        toast(
            `Transferred ${money.format(Number(result.amount))} ` +
            `from ${result.fromAccount} to ${result.toAccount}`
        );

        await refreshAfterChange();

    });

});

// =====================================================
// CLOSE ACCOUNT
// =====================================================

$('#close-account').addEventListener('click', async () => {

    if (!state.selected) {
        return;
    }

    const confirmed = window.confirm(
        `Close account ${state.selected}? Its history is kept, ` +
        `but it stops accepting transactions.`
    );

    if (!confirmed) {
        return;
    }

    try {

        const number = encodeURIComponent(state.selected);

        await api(`/accounts/${number}`, {
            method: 'DELETE'
        });

        toast(`Closed account ${state.selected}`);

        await refreshAfterChange();

    } catch (error) {

        toast(error.message, true);

    }

});

// =====================================================
// EVENT LISTENERS
// =====================================================

$('#accounts-body').addEventListener('click', event => {

    const button = event.target.closest('[data-open]');

    if (button) {
        openPassbook(button.dataset.open);
    }

});

$('#filter-type').addEventListener('change', () => {

    if (state.selected) {
        openPassbook(state.selected);
    }

});

// =====================================================
// APPLICATION START
// =====================================================

async function startApplication() {

    try {

        await loadAccounts();

    } catch (error) {

        toast(error.message, true);

        console.error('Banking application startup error:', error);

    }

}

startApplication();
