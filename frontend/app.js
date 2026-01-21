const API_BASE = window.location.origin;

const state = {
    products: [],
    orders: [],
    selectedProductId: null
};

const el = (id) => document.getElementById(id);
const show = (node) => { if (node) node.hidden = false; };
const hide = (node) => { if (node) node.hidden = true; };

window.addEventListener('DOMContentLoaded', () => {
    bindEvents();
    loadProducts();
    loadOrders();
});

function bindEvents() {
    const refresh = el('refreshProducts');
    if (refresh) refresh.addEventListener('click', (e) => { e.preventDefault(); loadProducts(); });

    const refreshOrders = el('refreshOrders');
    if (refreshOrders) refreshOrders.addEventListener('click', (e) => { e.preventDefault(); loadOrders(); });

    const createForm = el('createProductForm');
    if (createForm) createForm.addEventListener('submit', handleCreateProduct);

    const orderForm = el('orderForm');
    if (orderForm) orderForm.addEventListener('submit', handleOrderSubmit);
}

async function loadProducts() {
    const loading = el('productsLoading');
    const error = el('productsError');
    const empty = el('productsEmpty');

    show(loading);
    hide(error);
    hide(empty);

    try {
        const res = await fetch(`${API_BASE}/v1/products`, { headers: { 'Accept': 'application/json' } });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const products = await res.json();
        state.products = Array.isArray(products) ? products : [];
        renderProducts(state.products);
        populateProductSelect(state.products);
        if (!state.products.length) show(empty); else hide(empty);
    } catch (err) {
        if (error) error.textContent = `Error al cargar productos: ${err.message}`;
        show(error);
    } finally {
        hide(loading);
    }
}

function renderProducts(products) {
    const grid = el('productsGrid');
    if (!grid) return;
    grid.innerHTML = '';

    products.forEach((p) => {
        const card = document.createElement('div');
        card.className = 'product-card';
        card.dataset.id = p.id;
        card.innerHTML = `
            <div class="product-title">${escapeHtml(p.name || 'Producto')}</div>
            <div class="product-meta">ID: ${p.id}</div>
            <div class="product-meta">Precio: $${Number(p.price || 0).toFixed(2)}</div>
            <div class="product-meta">Stock: ${p.stock ?? '-'} unidades</div>
        `;
        card.addEventListener('click', () => selectProduct(p.id));
        grid.appendChild(card);
    });

    if (products.length) {
        selectProduct(products[0].id);
    }
}

function populateProductSelect(products) {
    const select = el('orderProductId');
    if (!select) return;
    select.innerHTML = '<option value="">Seleccione un producto</option>';
    products.forEach((p) => {
        const opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = `${p.name} ($${Number(p.price || 0).toFixed(2)})`;
        select.appendChild(opt);
    });
    if (state.selectedProductId) select.value = state.selectedProductId;
}

function selectProduct(id) {
    state.selectedProductId = id;
    document.querySelectorAll('.product-card').forEach((c) => {
        c.classList.toggle('selected', c.dataset.id === String(id));
    });
    const select = el('orderProductId');
    if (select) select.value = id;
    activateFlow(['flow-product']);
}

function validateNumber(value, min) {
    return typeof value === 'number' && !isNaN(value) && value >= min;
}

async function handleCreateProduct(evt) {
    evt.preventDefault();
    const name = (el('productName')?.value || '').trim();
    const price = parseFloat(el('productPrice')?.value || '0');
    const stock = parseInt(el('productStock')?.value || '0', 10);
    const loading = el('createProductLoading');
    const ok = el('createProductSuccess');
    const fail = el('createProductError');

    hide(ok); hide(fail); show(loading);

    try {
        if (!name || !validateNumber(price, 0) || !validateNumber(stock, 0)) {
            throw new Error('Nombre, precio y stock son obligatorios.');
        }
        const res = await fetch(`${API_BASE}/v1/products`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ name, price, stock })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
        if (ok) ok.textContent = 'Producto creado correctamente.';
        show(ok);
        evt.target.reset();
        await loadProducts();
    } catch (err) {
        if (fail) fail.textContent = err.message;
        show(fail);
    } finally {
        hide(loading);
    }
}

async function handleOrderSubmit(evt) {
    evt.preventDefault();
    const error = el('orderError');
    hide(error);

    const productId = parseInt(el('orderProductId')?.value || '0', 10);
    const quantity = parseInt(el('orderQuantity')?.value || '0', 10);
    const weight = parseFloat(el('orderWeight')?.value || '0');
    const distance = parseFloat(el('orderDistance')?.value || '0');

    if (!productId || !validateNumber(quantity, 1) || !validateNumber(weight, 0.1) || !validateNumber(distance, 1)) {
        if (error) error.textContent = 'Complete producto, cantidad, peso y distancia con valores válidos.';
        show(error);
        return;
    }

    const loading = el('orderLoading');
    show(loading);

    try {
        const res = await fetch(`${API_BASE}/v1/orders`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ productId, quantity, weight, distance })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
        displayOrderResult(data);
        await loadProducts();
        await loadOrders();
    } catch (err) {
        if (error) error.textContent = err.message;
        show(error);
    } finally {
        hide(loading);
    }
}

function displayOrderResult(order) {
    const setText = (id, value) => { const n = el(id); if (n) n.textContent = value; };
    setText('resultOrderId', order.id != null ? `#${order.id}` : '-');
    setText('resultStatus', order.status || 'CONFIRMED');
    setText('resultProductCost', `$${Number(order.subtotal ?? order.total ?? 0).toFixed(2)}`);
    setText('resultShippingCost', `$${Number(order.shippingCost ?? 0).toFixed(2)}`);
    const total = Number(order.total ?? 0) || Number(order.subtotal ?? 0) + Number(order.shippingCost ?? 0);
    setText('resultTotalCost', `$${Number(total).toFixed(2)}`);

    const json = el('orderResponseJson');
    if (json) json.textContent = JSON.stringify(order, null, 2);

    activateFlow(['flow-product', 'flow-order', 'flow-shipping', 'flow-result']);
}

function activateFlow(ids) {
    ['flow-product', 'flow-order', 'flow-shipping', 'flow-result'].forEach((key) => {
        const node = el(key);
        if (!node) return;
        node.classList.toggle('active', ids.includes(key));
    });
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = String(str ?? '');
    return div.innerHTML;
}

async function loadOrders() {
    const loading = el('ordersLoading');
    const error = el('ordersError');
    const empty = el('ordersEmpty');

    show(loading);
    hide(error);
    hide(empty);

    try {
        const res = await fetch(`${API_BASE}/v1/orders`, { headers: { 'Accept': 'application/json' } });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const orders = await res.json();
        state.orders = Array.isArray(orders) ? orders : [];
        renderOrders(state.orders);
        if (!state.orders.length) show(empty); else hide(empty);
    } catch (err) {
        if (error) error.textContent = `Error al cargar órdenes: ${err.message}`;
        show(error);
    } finally {
        hide(loading);
    }
}

function renderOrders(orders) {
    const container = el('ordersTable');
    if (!container) return;

    if (!orders.length) {
        container.innerHTML = '';
        return;
    }

    const table = document.createElement('table');
    table.className = 'orders-table';
    table.innerHTML = `
        <thead>
            <tr>
                <th>ID</th>
                <th>Producto</th>
                <th>Cantidad</th>
                <th>Subtotal</th>
                <th>Envío</th>
                <th>Total</th>
                <th>Estado</th>
            </tr>
        </thead>
        <tbody id="ordersTableBody"></tbody>
    `;
    container.innerHTML = '';
    container.appendChild(table);

    const tbody = el('ordersTableBody');
    orders.forEach((order) => {
        const productName = getProductName(order.productId);
        const subtotal = Number(order.subtotal ?? order.total ?? 0);
        const shipping = Number(order.shippingCost ?? 0);
        const total = Number(order.total ?? 0) || (subtotal + shipping);

        const row = document.createElement('tr');
        row.innerHTML = `
            <td>#${order.id ?? '-'}</td>
            <td>${escapeHtml(productName)}</td>
            <td>${order.quantity ?? '-'}</td>
            <td>$${subtotal.toFixed(2)}</td>
            <td>$${shipping.toFixed(2)}</td>
            <td><strong>$${total.toFixed(2)}</strong></td>
            <td><span class="badge">${escapeHtml(order.status ?? 'CONFIRMED')}</span></td>
        `;
        tbody.appendChild(row);
    });
}

function getProductName(productId) {
    const product = state.products.find(p => p.id === productId);
    return product ? product.name : `Producto #${productId}`;
}

