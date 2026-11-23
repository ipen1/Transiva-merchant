'use strict';

const screen = document.querySelector('#screen');
const selector = document.querySelector('#outlet');
const drawer = document.querySelector('#drawer');
const dialog = document.querySelector('#dialog');
const lists = ['products','sales','people','staff','attendance','materials','expenses','payments','surveys','discounts','types'];
const titles = {
  home:'Ringkasan', sale:'Transaksi', history:'Riwayat transaksi', products:'Produk & stok',
  materials:'Bahan baku', people:'Pelanggan', staff:'Pegawai', attendance:'Absensi',
  cash:'Rekap kas', report:'Laporan penjualan', outlets:'Outlet', discounts:'Diskon',
  types:'Tipe order', surveys:'Survei pelanggan', qris:'QRIS outlet', plan:'Langganan Kasir'
};
const dayFormat = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Makassar',year:'numeric',month:'2-digit',day:'2-digit'});
const rupiah = new Intl.NumberFormat('id-ID', {style:'currency',currency:'IDR',maximumFractionDigits:0});
const money = value => rupiah.format(value);
function date(value = Date.now()) {
  const parts = Object.fromEntries(dayFormat.formatToParts(value).map(x => [x.type,x.value]));
  return [parts.year,parts.month,parts.day].join('-');
}
function displayDate(value) {
  return new Intl.DateTimeFormat('id-ID', {dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Makassar'}).format(value);
}
let branches = [], outlet = '', book, revision, plan, active, busy = false, pending = null;
let current = 'home', cart = new Map(), query = '', selectedReceipt, reportMonth = date().slice(0,7);
let historyLimit = 50;
let csvKind = '', formBarcode = null, noticeTimer, nextRequest = 0;
const requests = new Map();

function el(tag, text = '', style = '') {
  const node = document.createElement(tag);
  if (text !== '') node.textContent = text;
  if (style) node.className = style;
  return node;
}
function group(style, ...children) {
  const node = el('div','',style);
  node.append(...children.filter(Boolean));
  return node;
}
function button(label, event, style = '') {
  const node = el('button',label,style);
  node.type = 'button';
  node.addEventListener('click',event);
  return node;
}
function mutator(label,event,style = '') {
  const result = button(label,event,style);
  result.disabled = busy || !active || Boolean(pending);
  return result;
}
function metric(label,value) {
  return group('card metric',el('p',label),el('strong',value));
}
function notice(message) {
  const toast = document.querySelector('#toast');
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => toast.hidden = true, 4700);
}
function request(action, payload) {
  return new Promise(resolve => {
    const key = String(++nextRequest);
    requests.set(Number(key),resolve);
    Transiva.request(key, action, outlet, payload == null ? '' : JSON.stringify(payload));
  });
}
function reply(key, payload) {
  const resolve = requests.get(key);
  if (resolve) {
    requests.delete(key);
    resolve(JSON.parse(payload));
  }
}
function uid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  const hex = Array.from(bytes,x => x.toString(16).padStart(2,'0')).join('');
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join('-');
}
function normalize(data) {
  for (const key of lists) if (!Array.isArray(data[key])) data[key] = [];
  if (!data.types.length) data.types = [{id:'here',name:'Makan di tempat'},{id:'take',name:'Bawa pulang'}];
  return data;
}
function clone(data) { return JSON.parse(JSON.stringify(data)); }
function find(key, id, data = book) { return data[key].find(item => item.id === id); }
function total(items, key = 'total') { return items.reduce((sum,item) => sum + item[key],0); }
function branchName() { return branches.find(branch => String(branch.id) === outlet)?.name || 'Outlet utama'; }

async function init(refresh = true) {
  if (busy || pending) return;
  busy = true;
  selector.disabled = true;
  screen.replaceChildren(el('p','Memuat data Kasir…'));
  if (refresh) {
    const result = await request('branches');
    if (!result.success || !result.branches?.length) {
      busy = false;
      screen.replaceChildren(el('p',result.message || 'Cabang belum tersedia.'),button('Muat ulang',() => init()));
      return;
    }
    branches = result.branches;
    const preferred = outlet || Transiva.selectedOutlet();
    outlet = String(branches.find(x => String(x.id) === preferred)?.id ?? branches[0].id);
    selector.replaceChildren(...branches.map(item => new Option(item.name,String(item.id))));
  }
  selector.value = outlet;
  const result = await request('load');
  busy = false;
  selector.disabled = false;
  if (!result.success) {
    book = null;
    screen.replaceChildren(el('p',result.message || 'Data Kasir belum bisa dimuat.'),button('Coba lagi',() => init()));
    return;
  }
  revision = result.revision;
  book = normalize(result.book);
  plan = result.plan;
  active = result.active;
  render();
}
async function commit(update, after) {
  if (busy || !active || pending) return;
  const next = clone(book);
  try { update(next); } catch (error) { notice(error.message); return; }
  const payload = {revision, request:uid(), book:next};
  const encoded = JSON.stringify(payload);
  // Conservative UTF-8 bound for the transport plus the server document limit.
  if (new TextEncoder().encode(encoded).length > 1950000) {
    notice('Kapasitas data outlet tercapai. Hubungi pengelola Transiva.');
    return;
  }
  pending = {payload, after};
  await sendPending();
}
async function sendPending() {
  if (!pending || busy) return;
  busy = true;
  selector.disabled = true;
  if (dialog.open) dialog.close();
  render();
  const result = await request('save',pending.payload);
  busy = false;
  selector.disabled = false;
  if (result.uncertain || !result.success && !result.code && /belum dapat terhubung/i.test(result.message || '')) {
    selector.disabled = true;
    render();
    return;
  }
  const submitted = pending;
  pending = null;
  if (!result.success) {
    notice(result.message || 'Data belum tersimpan.');
    if (result.code === 'POS_CONFLICT') {
      cart.clear();
      await init(false);
      return;
    }
    render();
    return;
  }
  revision = result.revision;
  book = submitted.payload.book;
  if (submitted.after) submitted.after();
  render();
  notice('Data tersimpan.');
}

function go(page) {
  if (!book || busy || pending) return;
  drawer.hidden = true;
  current = page;
  render();
  window.scrollTo(0,0);
}
function back() {
  if (busy || pending) { notice('Selesaikan penyimpanan sebelum keluar.'); return; }
  if (dialog.open) dialog.close();
  else if (!drawer.hidden) drawer.hidden = true;
  else if (current === 'receipt') go('history');
  else if (current !== 'home') go('home');
  else if (!cart.size || confirm('Keluar dan kosongkan keranjang?')) Transiva.exit();
}
selector.addEventListener('change',() => {
  if (cart.size && !confirm('Kosongkan keranjang dan pindah outlet?')) {
    selector.value = outlet;
    return;
  }
  outlet = selector.value;
  cart.clear();
  current = 'home';
  init(false);
});
document.querySelector('#menu').onclick = () => { if (!busy && !pending) drawer.hidden = false; };
document.querySelector('#close-menu').onclick = () => drawer.hidden = true;
drawer.addEventListener('click',event => { if (event.target === drawer) drawer.hidden = true; });
document.querySelector('#exit').onclick = () => {
  if (busy || pending) { notice('Selesaikan penyimpanan sebelum keluar.'); return; }
  if (!cart.size || confirm('Keluar dan kosongkan keranjang?')) Transiva.exit();
};
function render() {
  if (!book) return;
  const fast = ['home','sale','history'];
  document.querySelector('#dock').replaceChildren(group('', ...fast.map(key =>
    button(titles[key],() => go(key),current === key ? 'selected' : ''))));
  document.querySelector('#links').replaceChildren(...Object.entries(titles).map(([key,label]) =>
    button(label,() => go(key),current === key ? 'selected' : 'quiet')));
  screen.className = 'stack';
  if (busy || pending) {
    screen.replaceChildren(el('h1',busy ? 'Menyimpan data' : 'Status penyimpanan belum pasti'),
      el('p',busy ? 'Tunggu konfirmasi dari server Kasir.' : 'Gunakan tombol di bawah untuk memeriksa ulang penyimpanan yang sama.'),
      busy ? el('progress') : button('Periksa penyimpanan',sendPending,'primary'));
    return;
  }
  screen.replaceChildren();
  if (current !== 'receipt') {
    screen.append(el('h1',titles[current]));
    if (!active) screen.append(group('card',el('p','Langganan outlet di luar masa aktif. Data tersimpan dan dapat dibaca.')));
  }
  pages[current]();
}

function empty(items,label) {
  if (!items.length) screen.append(group('card',el('p',label)));
}
function row(name, detail, actions = []) {
  return group('card flex split',group('stack grow',el('h3',name),detail ? el('p',detail) : null),...actions);
}
function list(key, caption, detail, fields, options = {}) {
  const items = book[key];
  screen.append(group('flex',mutator('Tambah ' + caption.toLowerCase(),() => edit(key,caption,fields,null,options),'primary'),
    ...(options.buttons || [])));
  empty(items,'Belum ada ' + caption.toLowerCase() + '.');
  for (const item of items) screen.append(row(item.name,detail(item),[
    mutator('Edit',() => edit(key,caption,fields,item,options))
  ]));
}
function text(name, label, extra = {}) { return {name,label,...extra}; }
function number(name,label,min = 0,max = 1000000000,step = 1) { return {name,label,type:'number',min,max,step}; }
function pick(name,label,options) { return {name,label,options}; }
function choices(items) { return items.map(item => [item.id,item.name]); }

function form(title, fields, values, submit, label = 'Simpan') {
  if (dialog.open) dialog.close();
  const content = el('form','','stack');
  const controls = {};
  formBarcode = null;
  for (const field of fields) {
    const wrapper = el('label',field.label);
    const input = el(field.options ? 'select' : 'input');
    if (field.options) for (const [value,name] of field.options) input.append(new Option(name,value));
    else {
      input.type = field.type || 'text';
      if (input.type === 'number') {
        input.min = field.min;
        input.max = field.max;
        input.step = field.step;
      } else {
        input.maxLength = field.maxLength ?? 120;
        input.autocomplete = 'off';
      }
    }
    if (!field.optional) input.required = true;
    if (values[field.name] !== undefined) input.value = values[field.name];
    else if (input.type === 'number' && field.min === 0) input.value = 0;
    controls[field.name] = input;
    wrapper.append(input);
    if (field.name === 'barcode') {
      formBarcode = input;
      wrapper.append(button('Scan dengan kamera',() => Transiva.scan(),'quiet'));
    }
    content.append(wrapper);
  }
  const save = el('button',label,'primary');
  save.type = 'submit';
  content.append(group('flex split',button('Batal',() => dialog.close(),'quiet'),save));
  content.addEventListener('submit',event => {
    event.preventDefault();
    const values = {};
    for (const field of fields) {
      const value = controls[field.name].value.trim();
      if (!field.optional && !value) { notice(field.label + ' wajib diisi.'); return; }
      values[field.name] = field.type === 'number' ? Number(value) : value;
    }
    submit(values);
  });
  dialog.replaceChildren(el('h2',title),content);
  dialog.showModal();
}
function edit(key, caption, fields, item, options) {
  form((item ? 'Edit ' : 'Tambah ') + caption.toLowerCase(),fields,item || {}, values =>
    commit(next => {
      if (options.validate) options.validate(next,values,item);
      if (item) Object.assign(find(key,item.id,next),values);
      else next[key].push({...values,id:uid()});
    }));
}
function optional(name,label,maxLength) { return text(name,label,{optional:true,maxLength}); }
function codeUnique(data, barcode, id) {
  if (barcode && data.products.some(p => p.id !== id && p.barcode === barcode)) {
    throw new Error('Barcode sudah dipakai produk lain.');
  }
}
const productFields = [
  text('name','Nama produk'),optional('barcode','Barcode',80), number('price','Harga jual (Rp)',1),
  number('stock','Stok unit',0,999999),number('tax','Pajak produk (%)',0,100,.01)
];
const personFields = [text('name','Nama pelanggan'),optional('phone','Nomor telepon',40),
  number('member','Diskon anggota (%)',0,100,.01)];

function reportSales() {
  return book.sales.filter(item => {
    const d = date(item.at);
    return !plan || d >= plan.starts_on && d <= plan.ends_on;
  });
}
function displayHome() {
  const sales = reportSales();
  const today = date();
  const openDebt = book.sales.reduce((sum,sale) => sum + outstanding(sale),0);
  screen.append(group('hero',el('div','PENCATATAN TOKO','eyebrow'),el('h2',branchName()),
    el('p','Mulai transaksi, cek stok, dan pantau penjualan outlet dari satu tempat.'),
    mutator('Buka transaksi',() => go('sale'))));
  screen.append(group('metrics',
    metric('Penjualan hari ini',money(total(sales.filter(s => date(s.at) === today)))),
    metric('Penjualan bulan ini',money(total(sales.filter(s => date(s.at).startsWith(today.slice(0,7)))))),
    metric('Kasbon belum lunas',money(openDebt)),metric('Produk stok rendah',String(book.products.filter(p => p.stock <= 5).length))));
  screen.append(el('h2','Kelola outlet','section'),group('grid',
    ...['products','cash','report','people','staff','outlets'].map(key => button(titles[key],() => go(key)))));
}
function displayProduct() {
  list('products','Produk',p => money(p.price) + ' · Stok ' + p.stock + ' · Pajak ' + p.tax + '%'
    + (p.barcode ? ' · ' + p.barcode : ''),productFields,{
      validate:(next,values,item) => codeUnique(next,values.barcode,item?.id),
      buttons:[button('Unduh template',() => exportCsv('template-produk.csv',productCsv,[])),
        mutator('Impor CSV',() => beginImport('products')),
        button('Ekspor CSV',() => exportCsv('produk-kasir.csv',productCsv,book.products.map(p =>
          [p.name,p.barcode,p.price,p.stock,p.tax])))]
    });
  if (book.products.length) {
    screen.append(el('h2','Resep produk','section'));
    for (const product of book.products) {
      const recipe = product.recipe || {};
      const detail = Object.entries(recipe).map(([id,qty]) => {
        const material = find('materials',id);
        return material ? qty + ' ' + material.unit + ' ' + material.name : '';
      }).filter(Boolean).join(' · ');
      screen.append(row(product.name,detail || 'Belum ada resep.',[
        mutator('Atur resep',() => recipeForm(product))
      ]));
    }
  }
}
function recipeForm(product) {
  if (!book.materials.length) { notice('Tambahkan bahan baku terlebih dahulu.'); return; }
  const fields = book.materials.map(m => number(m.id,m.name + ' (' + m.unit + ' per produk)',0,100000,.001));
  form('Resep ' + product.name,fields,product.recipe || {},values => commit(next => {
    find('products',product.id,next).recipe = Object.fromEntries(Object.entries(values).filter(([,v]) => v > 0));
  }));
}
function beginImport(kind) { csvKind = kind; Transiva.importCsv(); }
function scanResult(code) {
  if (formBarcode && dialog.open) { formBarcode.value = code.slice(0,80); return; }
  if (current !== 'sale') return;
  const product = book.products.find(p => p.barcode === code);
  if (product) addToCart(product.id,1);
  else notice('Barcode belum terdaftar.');
}
function addToCart(id, amount) {
  const product = find('products',id);
  const qty = (cart.get(id) || 0) + amount;
  if (!product || qty > product.stock) { notice('Stok produk tidak mencukupi.'); return; }
  if (qty <= 0) cart.delete(id);
  else cart.set(id,qty);
  render();
}
function cartItems(data = book) {
  return Array.from(cart,([id,qty]) => {
    const product = find('products',id,data);
    if (!product || product.stock < qty) throw new Error('Stok produk berubah. Periksa keranjang.');
    return {id,name:product.name,qty,price:product.price,tax:product.tax};
  });
}
function calculate(items, promo, membership, points) {
  const subtotal = items.reduce((sum,item) => sum + item.price * item.qty,0);
  const rate = Math.min(100,promo + membership);
  const tax = items.reduce((sum,item) => sum + Math.round(item.qty * item.price * (1-rate/100) * item.tax / 100),0);
  const discount = Math.round(subtotal * rate / 100);
  const redeem = points * 100;
  return {subtotal,tax,discount,redeem,total:subtotal-discount+tax-redeem};
}
function displaySale() {
  const input = el('input');
  input.type = 'search';
  input.placeholder = 'Cari nama atau barcode';
  input.setAttribute('aria-label','Cari produk');
  input.value = query;
  const results = group('stack');
  function catalog() {
    const matching = book.products.filter(p => (p.name + ' ' + p.barcode).toLowerCase().includes(query.trim().toLowerCase()));
    results.replaceChildren(...matching.slice(0,60).map(product =>
      row(product.name,money(product.price) + ' · Stok ' + product.stock + ' · Pajak ' + product.tax + '%',[
        mutator('Tambah',() => addToCart(product.id,1),'quiet')
      ])));
    if (!matching.length) results.append(el('p','Produk belum ditemukan.'));
    if (matching.length > 60) results.append(el('p','Persempit pencarian untuk melihat produk lainnya.'));
  }
  input.oninput = () => { query = input.value; catalog(); };
  input.onkeydown = event => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    const product = book.products.find(p => p.barcode && p.barcode === input.value.trim());
    if (product) { query = ''; addToCart(product.id,1); }
  };
  catalog();
  const basket = group('card stack',el('h2','Keranjang'));
  for (const item of cartItems()) {
    basket.append(group('line flex split',group('grow',el('h3',item.name),el('p',money(item.price) + ' × ' + item.qty)),
      button('−',() => addToCart(item.id,-1),'quiet'),button('+',() => addToCart(item.id,1),'quiet')));
  }
  if (cart.size) {
    const amounts = calculate(cartItems(),0,0,0);
    basket.append(group('flex split',el('strong',money(amounts.total),'amount'),
      mutator('Lanjut bayar',checkout,'primary')));
  } else basket.append(el('p','Tambahkan produk untuk mulai transaksi.'));
  screen.append(basket,group('flex',group('grow',input),mutator('Scan',() => Transiva.scan())),results);
}
function checkout() {
  const fields = [
    pick('type','Tipe order',choices(book.types)),
    pick('customer','Pelanggan',[['walk','Pelanggan umum'],...choices(book.people)]),
    pick('staff','Petugas',[['owner','Pemilik outlet'],...choices(book.staff)]),
    pick('discount','Diskon',[['none','Tanpa diskon'],...choices(book.discounts)]),
    number('points','Tukar poin (1 poin = Rp100)',0,100000),
    pick('method','Cara bayar',[['cash','Tunai'],['qris','QRIS · konfirmasi manual'],['debt','Utang / kasbon']])
  ];
  form('Rincian transaksi',fields,{}, preview => {
    const person = find('people',preview.customer);
    if (preview.method === 'debt' && !person) { notice('Pilih pelanggan untuk kasbon.'); return; }
    if (preview.points > (person?.points || 0)) { notice('Poin pelanggan tidak mencukupi.'); return; }
    const amounts = calculate(cartItems(),find('discounts',preview.discount)?.percent || 0,person?.member || 0,preview.points);
    if (amounts.total < 0) { notice('Nilai poin melebihi jumlah tagihan.'); return; }
    const payFields = preview.method === 'cash' ? [number('cash','Uang diterima (Rp)',amounts.total,100000000000)]
      : preview.method === 'qris' ? [text('reference','Referensi QRIS yang sudah dikonfirmasi', {maxLength:80})] : [];
    const paymentName = {cash:'Tunai',qris:'QRIS',debt:'Kasbon'}[preview.method];
    form('Konfirmasi ' + paymentName,payFields,{},pay => recordSale(preview,pay),
      'Simpan ' + money(amounts.total));
    if (preview.method === 'qris' && book.qrImage) dialog.querySelector('form').before(qrImage());
    dialog.querySelector('h2').after(el('p',
      'Subtotal ' + money(amounts.subtotal) + ' · Diskon ' + money(amounts.discount) +
      ' · Pajak ' + money(amounts.tax) + ' · Poin ' + money(amounts.redeem) +
      (preview.method === 'qris' ? ' · Cek dana masuk pada aplikasi penyedia QRIS outlet.' : '')));
  },'Lihat total');
}
function recordSale(selection,pay) {
  const id = uid();
  commit(next => {
    const items = cartItems(next), person = find('people',selection.customer,next);
    const amounts = calculate(items,find('discounts',selection.discount,next)?.percent || 0,person?.member || 0,selection.points);
    if (selection.method === 'qris' && next.sales.some(s => s.reference &&
      s.reference.toLowerCase() === pay.reference.toLowerCase())) throw new Error('Referensi QRIS sudah tercatat.');
    const used = new Map();
    for (const item of items) {
      const product = find('products',item.id,next);
      product.stock -= item.qty;
      for (const [material,qty] of Object.entries(product.recipe || {})) {
        used.set(material,(used.get(material) || 0) + Math.round(qty * 1000) * item.qty);
      }
    }
    for (const [id,qty] of used) {
      const material = find('materials',id,next);
      const available = material && Math.round(material.stock * 1000);
      if (!material || available < qty) throw new Error('Stok bahan ' + (material?.name || '') + ' tidak cukup.');
      material.stock = (available-qty)/1000;
    }
    if (person) {
      const earned = selection.method === 'debt' && amounts.total > 0 ? 0 : Math.floor(amounts.total/10000);
      person.points = (person.points || 0) - selection.points + earned;
    }
    next.sales.push({
      ...amounts,id,at:Date.now(),items,method:selection.method,
      customer:person?.id || '', customerName:person?.name || 'Pelanggan umum',
      staff:find('staff',selection.staff,next)?.name || 'Pemilik outlet',
      type:find('types',selection.type,next).name,outlet:branchName(),
      cash:pay.cash ?? 0,reference:pay.reference ?? ''
    });
  },() => { cart.clear(); selectedReceipt = id; current = 'receipt'; });
}

function outstanding(sale, data = book) {
  return sale.method === 'debt' ? sale.total - total(data.payments.filter(p => p.sale === sale.id),'amount') : 0;
}
function displayHistory() {
  empty(book.sales,'Belum ada transaksi.');
  for (const sale of [...book.sales].reverse().slice(0,historyLimit)) {
    const debt = outstanding(sale);
    screen.append(row(money(sale.total),
      displayDate(sale.at) + ' · ' + sale.customerName + ' · ' + sale.method.toUpperCase()
      + (debt ? ' · Sisa ' + money(debt) : ''),
      [button('Struk',() => { selectedReceipt = sale.id; go('receipt'); })]));
  }
  if (book.sales.length > historyLimit) screen.append(button('Lihat transaksi sebelumnya',() => {
    historyLimit += 50;
    render();
  }));
}
function payDebt(sale) {
  const due = outstanding(sale);
  form('Pembayaran kasbon tunai',[
    number('amount','Jumlah pembayaran (Rp)',1,due)
  ],{amount:due},values => commit(next => {
    const currentDue = outstanding(sale,next);
    if (values.amount > currentDue) throw new Error('Pembayaran melebihi sisa kasbon.');
    next.payments.push({...values,sale:sale.id,at:Date.now(),id:uid()});
    if (values.amount === currentDue) {
      const customer = find('people',sale.customer,next);
      if (customer) customer.points = (customer.points || 0) + Math.floor(sale.total/10000);
    }
  }));
}
function displayReceipt() {
  const sale = find('sales',selectedReceipt);
  if (!sale) return;
  const receipt = group('card receipt stack',el('h1','Transiva'),el('h2',sale.outlet),
    el('p',displayDate(sale.at)),el('p','No. ' + sale.id.slice(0,12).toUpperCase()),
    el('p',sale.customerName + ' · ' + sale.type),el('p','Petugas ' + sale.staff));
  for (const item of sale.items) receipt.append(group('line flex split',
    el('p',item.qty + ' × ' + item.name,'grow'),el('p',money(item.qty * item.price))));
  const values = [['Subtotal',sale.subtotal],['Diskon',sale.discount],['Pajak',sale.tax],['Tukar poin',sale.redeem],['Total',sale.total]];
  if (sale.method === 'cash') values.push(['Uang diterima',sale.cash],['Kembalian',sale.cash-sale.total]);
  if (sale.method === 'debt') values.push(['Sisa kasbon',outstanding(sale)]);
  for (const [title,value] of values) receipt.append(group('flex split',el('span',title),el('strong',money(value))));
  receipt.append(el('p','Metode ' + sale.method.toUpperCase() + (sale.reference ? ' · Ref. ' + sale.reference : '')));
  screen.append(receipt,group('flex no-print',button('Riwayat',() => go('history')),
    button('Cetak struk',() => Transiva.print(),'primary'),
    outstanding(sale) ? mutator('Catat pembayaran kasbon',() => payDebt(sale)) : null));
}
function displayPeople() {
  list('people','Pelanggan',p => (p.phone || 'Tanpa nomor') + ' · ' + (p.points || 0) + ' poin · Diskon ' + p.member + '%',
    personFields,{buttons:[button('Unduh template',() => exportCsv('template-pelanggan.csv',personCsv,[])),
      mutator('Impor CSV',() => beginImport('people'))]});
}
function displayAttendance() {
  if (!book.staff.length) screen.append(row('Belum ada pegawai','Tambahkan pegawai untuk mencatat absensi.',
    [button('Kelola pegawai',() => go('staff'))]));
  for (const staff of book.staff) {
    const shift = [...book.attendance].reverse().find(x => x.staff === staff.id && !x.end);
    screen.append(row(staff.name,shift ? 'Masuk ' + displayDate(shift.at) : 'Di luar shift',[
      mutator(shift ? 'Catat pulang' : 'Catat masuk',() => commit(next => {
        if (shift) find('attendance',shift.id,next).end = Date.now();
        else next.attendance.push({id:uid(),staff:staff.id,name:staff.name,at:Date.now()});
      }))
    ]));
  }
  for (const shift of [...book.attendance].reverse().slice(0,60)) {
    screen.append(row(shift.name,displayDate(shift.at) + ' → ' + (shift.end ? displayDate(shift.end) : 'Sedang bekerja')));
  }
}
function displayCash() {
  const day = date();
  const receipts = total(book.sales.filter(s => s.method === 'cash' && date(s.at) === day));
  const settlement = total(book.payments.filter(p => date(p.at) === day),'amount');
  const expenses = total(book.expenses.filter(e => date(e.at) === day),'amount');
  const adjustment = Number(book.cashOpening?.[day] || 0);
  screen.append(group('metrics',metric('Kas penjualan',money(receipts)),metric('Pelunasan kasbon',money(settlement)),
    metric('Pengeluaran hari ini',money(expenses)),metric('Perkiraan saldo laci',money(adjustment+receipts+settlement-expenses))));
  screen.append(group('flex',mutator('Catat pengeluaran',() => form('Pengeluaran tunai',[
    text('name','Keperluan'),number('amount','Jumlah (Rp)',1)
  ],{},values => commit(next => next.expenses.push({...values,id:uid(),at:Date.now()}))),'primary'),
  mutator('Modal awal ' + money(adjustment),() => form('Modal awal hari ini',[number('amount','Modal awal (Rp)')],
    {amount:adjustment},values => commit(next => {
      next.cashOpening = next.cashOpening || {};
      next.cashOpening[day] = values.amount;
    })))));
  for (const entry of [...book.expenses].reverse().slice(0,60)) {
    screen.append(row(entry.name,displayDate(entry.at) + ' · ' + money(entry.amount)));
  }
}
function displayReport() {
  const field = el('input');
  field.type = 'month';
  field.value = reportMonth;
  field.setAttribute('aria-label','Bulan laporan');
  field.onchange = () => { if (field.value) { reportMonth = field.value; render(); } };
  const month = reportSales().filter(s => date(s.at).startsWith(reportMonth));
  const income = total(month);
  screen.append(field,group('metrics',metric('Penjualan',money(income)),metric('Transaksi',String(month.length)),
    metric('Pajak produk',money(total(month,'tax'))),metric('Diskon',money(total(month,'discount')))));
  const grouped = new Map();
  for (const sale of month) grouped.set(date(sale.at),(grouped.get(date(sale.at)) || 0) + sale.total);
  const peak = Math.max(1,...grouped.values());
  const trend = group('card stack',el('h2','Tren harian'));
  if (!grouped.size) trend.append(el('p','Belum ada penjualan pada periode ini.'));
  for (const [day,value] of [...grouped.entries()].sort()) {
    const bar = el('progress');
    bar.max = peak;
    bar.value = value;
    bar.setAttribute('aria-label',day + ' ' + money(value));
    trend.append(group('flex split',el('p',day),el('strong',money(value))),bar);
  }
  screen.append(trend,button('Ekspor laporan CSV',() => exportCsv('laporan-penjualan.csv',
    ['tanggal','id','metode','subtotal','diskon','pajak','poin_rp','total'],
    month.map(s => [date(s.at),s.id,s.method,s.subtotal,s.discount,s.tax,s.redeem,s.total]))));
}
function displayOutlets() {
  for (const branch of branches) {
    const id = String(branch.id);
    screen.append(row(branch.name,id === outlet ? 'Outlet aktif' : 'Data kasir tersedia per outlet',[
      id === outlet ? null : button('Pilih',() => {
        selector.value = id;
        selector.dispatchEvent(new Event('change'));
      })
    ].filter(Boolean)));
  }
  screen.append(button('Kelola cabang Merchant',() => Transiva.branches(),'primary'),
    button('Segarkan outlet',() => init()));
}
function displaySurveys() {
  screen.append(mutator('Catat survei pelanggan',() => form('Survei pelanggan',[
    pick('customer','Pelanggan',[['anonymous','Anonim'],...choices(book.people)]),
    number('score','Kepuasan (1–5)',1,5),optional('note','Masukan',350)
  ],{},values => commit(next => next.surveys.push({...values,id:uid(),at:Date.now(),
    name:find('people',values.customer,next)?.name || 'Anonim'}))),'primary'));
  empty(book.surveys,'Belum ada survei pelanggan.');
  if (book.surveys.length) screen.append(metric('Rata-rata kepuasan',(total(book.surveys,'score') / book.surveys.length).toFixed(1) + ' / 5'));
  for (const survey of [...book.surveys].reverse()) {
    screen.append(row(survey.name + ' · ' + survey.score + '/5',survey.note || displayDate(survey.at)));
  }
}
function qrImage() {
  const image = el('img','','merchant-qr');
  image.alt = 'QRIS pembayaran ' + branchName();
  if (/^data:image\/jpeg;base64,[A-Za-z0-9+/]+=*$/.test(book.qrImage || '')) image.src = book.qrImage;
  return image;
}
function qrResult(image) {
  if (current !== 'qris' || !/^data:image\/jpeg;base64,[A-Za-z0-9+/]+=*$/.test(image)) return;
  commit(next => { next.qrImage = image; });
}
function displayQris() {
  screen.append(group('card stack',el('p','Pasang gambar QRIS statis resmi outlet. Pada transaksi, cocokkan nominal dan dana masuk di aplikasi penyedia sebelum mencatat referensi pembayaran.'),
    book.qrImage ? qrImage() : el('p','Gambar QRIS belum dipasang.'),
    group('flex',mutator(book.qrImage ? 'Ganti gambar QRIS' : 'Pasang gambar QRIS',() => Transiva.pickQrImage(),'primary'),
      book.qrImage ? mutator('Hapus gambar',() => {
        if (confirm('Hapus tampilan gambar QRIS outlet ini?')) commit(next => delete next.qrImage);
      }) : null)));
}
function displayPlan() {
  screen.append(group('card stack',el('h2',plan ? (active ? 'Langganan aktif' : 'Di luar masa aktif') : 'Akses evaluasi'),
    el('p',plan ? plan.starts_on + ' sampai ' + plan.ends_on : 'Belum ada periode langganan untuk outlet ini.'),
    el('p',plan ? 'Laporan mengikuti tanggal paket di outlet ini. Hubungi pengelola Transiva untuk perpanjangan.'
      : 'Pengelola Transiva dapat mengatur tanggal aktif untuk outlet ini.')));
}
const pages = {
  home:displayHome, sale:displaySale, history:displayHistory, receipt:displayReceipt, products:displayProduct,
  cash:displayCash, report:displayReport, people:displayPeople, attendance:displayAttendance,
  outlets:displayOutlets, surveys:displaySurveys, qris:displayQris, plan:displayPlan,
  staff:() => list('staff','Pegawai',p => p.role + (p.phone ? ' · ' + p.phone : ''),
    [text('name','Nama pegawai'),text('role','Jabatan'),optional('phone','Nomor telepon',40)]),
  materials:() => list('materials','Bahan baku',p => 'Stok ' + p.stock + ' ' + p.unit,
    [text('name','Nama bahan'),text('unit','Satuan (mis. gram)'),number('stock','Stok bahan',0,999999,.001)]),
  discounts:() => list('discounts','Diskon',p => p.percent + '% per transaksi',
    [text('name','Nama diskon'),number('percent','Potongan (%)',1,100,.01)]),
  types:() => list('types','Tipe order',() => '',[text('name','Nama tipe order')])
};

const productCsv = ['nama','barcode','harga','stok','pajak'];
const personCsv = ['nama','telepon','diskon_anggota'];
function exportCsv(filename,columns,records) {
  const fields = line => line.map(value => {
    let content = String(value);
    if (/^[\s]*[=+\-@]/.test(content) || /^[\t\r\n]/.test(content)) content = "'" + content;
    return '"' + content.replace(/"/g,'""') + '"';
  }).join(',');
  Transiva.exportCsv(filename,[columns,...records].map(fields).join('\r\n') + '\r\n');
}
function parseCsv(content) {
  const output = [];
  let row = [], field = '', quoted = false, closed = false;
  for (let i = 0; i < content.length; i++) {
    const char = content[i];
    if (quoted) {
      if (char === '"' && content[i+1] === '"') { field += '"'; i++; }
      else if (char === '"') { quoted = false; closed = true; }
      else field += char;
    } else if (char === '"' && !field && !closed) quoted = true;
    else if (char === ',' || char === '\n' || char === '\r') {
      row.push(field.trim());
      field = '';
      closed = false;
      if (char !== ',') {
        if (char === '\r' && content[i+1] === '\n') i++;
        if (row.some(Boolean)) output.push(row);
        row = [];
      }
    } else if (closed || char === '"') throw new Error('Tanda kutip CSV tidak valid.');
    else field += char;
  }
  if (quoted) throw new Error('Tanda kutip CSV belum ditutup.');
  row.push(field.trim());
  if (row.some(Boolean)) output.push(row);
  return output;
}
function csvNumber(value,min,max,decimals = false) {
  if (!(decimals ? /^\d+(\.\d{1,2})?$/ : /^\d+$/).test(value) || Number(value)<min || Number(value)>max) {
    throw new Error('Angka berada di luar batas atau formatnya tidak valid.');
  }
  return Number(value);
}
function importResult(content) {
  if (!csvKind || !book || busy || pending || !active) return;
  const kind = csvKind;
  csvKind = '';
  let rows;
  try {
    rows = parseCsv(content.replace(/^\uFEFF/,''));
    const cols = kind === 'products' ? productCsv : personCsv;
    if (JSON.stringify(rows.shift()) !== JSON.stringify(cols) || !rows.length || rows.length > 300) {
      throw new Error('Gunakan template CSV Kasir dengan 1–300 baris data.');
    }
  } catch (error) { notice(error.message); return; }
  if (!confirm('Impor ' + rows.length + ' baris baru ke ' + branchName() + '?')) return;
  commit(next => rows.forEach((row,index) => {
    try {
      const [name,code,value,stock,tax] = row;
      if (!name || name.length > 120 || row.length !== (kind === 'products' ? 5 : 3)) {
        throw new Error('Kolom atau nama tidak valid.');
      }
      if (kind === 'products') {
        if (code.length > 80) throw new Error('Barcode terlalu panjang.');
        codeUnique(next,code);
        next.products.push({id:uid(),name,barcode:code,price:csvNumber(value,1,1000000000),
          stock:csvNumber(stock,0,999999),tax:csvNumber(tax,0,100,true)});
      } else {
        if (code.length > 40) throw new Error('Nomor telepon terlalu panjang.');
        next.people.push({id:uid(),name,phone:code,member:csvNumber(value,0,100,true),points:0});
      }
    } catch (error) { throw new Error('Baris ' + (index + 2) + ' · ' + error.message); }
  }));
}

init();
