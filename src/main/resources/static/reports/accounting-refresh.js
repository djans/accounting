(function(){
    const btn = document.getElementById('acct-refresh');
    const spinner = document.getElementById('acct-spinner');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function formatCurrency(v){ try{ const n = Number(v); return new Intl.NumberFormat(undefined, {style:'currency', currency:'CAD'}).format(isNaN(n)?0:n); }catch(e){return v;} }
    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, character => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        })[character]);
    }

    window.showTransactions = async function(accountId, accountName) {
        const modal = new bootstrap.Modal(document.getElementById('transactionModal'));
        document.getElementById('modalAccountName').innerText = accountName;
        const tbody = document.getElementById('transaction-tbody');
        const noTrans = document.getElementById('no-transactions');
        tbody.innerHTML = '<tr><td colspan="5" class="text-center"><span class="spinner-border spinner-border-sm"></span> Loading...</td></tr>';
        noTrans.style.display = 'none';
        modal.show();

        try {
            const resp = await fetch(`/api/accounting-reports/account-transactions/${accountId}`);
            if (resp.ok) {
                const data = await resp.json();
                if (data.length === 0) {
                    tbody.innerHTML = '';
                    noTrans.style.display = 'block';
                } else {
                    tbody.innerHTML = data.map(t => `
                        <tr>
                            <td>${t.date}</td>
                            <td><a href="/journals/${t.journalId}">${t.journalNumber}</a></td>
                            <td>${t.description || ''}</td>
                            <td class="num">${formatCurrency(t.debit)}</td>
                            <td class="num">${formatCurrency(t.credit)}</td>
                        </tr>
                    `).join('');
                }
            } else {
                tbody.innerHTML = '<tr><td colspan="5" class="text-danger">Error loading transactions.</td></tr>';
            }
        } catch (e) {
            console.error(e);
            tbody.innerHTML = '<tr><td colspan="5" class="text-danger">Failed to connect to server.</td></tr>';
        }
    };

    function createAccountLink(acct) {
        return `
            <a href="#"
               class="account-transactions-link"
               data-account-id="${escapeHtml(acct.accountId)}"
               data-account-name="${escapeHtml(acct.accountName)}"
               style="text-decoration: none; color: var(--portal-primary); font-weight: 500;">
               ${escapeHtml(acct.accountName)}
            </a>
        `;
    }

    document.addEventListener('click', function(event) {
        const link = event.target.closest('.account-transactions-link');
        if (!link) {
            return;
        }

        event.preventDefault();
        window.showTransactions(link.dataset.accountId, link.dataset.accountName);
    });

    async function refresh(){
        setLoading(true);
        try{
            const t = await fetch('/api/accounting-reports/trial-balance', {cache:'no-store'});
            if(t.ok){
                const jt = await t.json();
                const tbody = document.getElementById('tb-tbody');
                if(tbody){ 
                    tbody.innerHTML = Object.values(jt.accounts||{}).map(acct=>`
                        <tr>
                            <td>${acct.accountNumber}</td>
                            <td>${createAccountLink(acct)}</td>
                            <td><span class="badge-pill">${acct.accountType}</span></td>
                            <td class='num'>${formatCurrency(acct.debitBalance)}</td>
                            <td class='num'>${formatCurrency(acct.creditBalance)}</td>
                            <td class='num'>${formatCurrency(acct.balance)}</td>
                        </tr>`).join(''); 
                }
                
                // Update Trial Balance totals
                const tfoot = document.querySelector('.portal-card:has(#tb-tbody) tfoot');
                if(tfoot && jt.categoryTotals){
                    tfoot.innerHTML = `
                        <tr style="font-weight:600; background-color: #f8fafc;">
                            <td colspan="5" style="text-align:right">Total Assets</td>
                            <td class="num">${formatCurrency(jt.categoryTotals.ASSET)}</td>
                        </tr>
                        <tr style="font-weight:600; background-color: #f8fafc;">
                            <td colspan="5" style="text-align:right">Total Liabilities</td>
                            <td class="num">${formatCurrency(jt.categoryTotals.LIABILITY)}</td>
                        </tr>
                        <tr style="font-weight:600; background-color: #f8fafc;">
                            <td colspan="5" style="text-align:right">Total Equity</td>
                            <td class="num">${formatCurrency(jt.categoryTotals.EQUITY)}</td>
                        </tr>
                        <tr style="font-weight:600; border-top: 2px solid #e2e8f0;">
                            <td colspan="3" style="text-align:right">Totals</td>
                            <td class="num">${formatCurrency(jt.totalDebits)}</td>
                            <td class="num">${formatCurrency(jt.totalCredits)}</td>
                            <td></td>
                        </tr>
                    `;
                }
                
                // Update Balanced status
                const tbHeader = document.querySelector('.portal-card:has(#tb-tbody) .portal-card-title');
                if(tbHeader){
                    const badge = jt.balanced ? '<span class="badge-pill paid">Balanced</span>' : '<span class="badge-pill overdue">Out of balance</span>';
                    tbHeader.innerHTML = `Trial Balance ${badge}`;
                }
            }
            const b = await fetch('/api/accounting-reports/balance-sheet',{cache:'no-store'});
            if(b.ok){
                const jb = await b.json();
                const as = document.getElementById('bs-assets'); 
                if(as){ 
                    let html = (jb.assets||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${createAccountLink(r)}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join('');
                    html += `<tr style="font-weight:600"><td colspan="2" style="text-align:right">Total Assets</td><td class="num">${formatCurrency(jb.totalAssets)}</td></tr>`;
                    as.innerHTML = html;
                }
                const ls = document.getElementById('bs-liabilities'); 
                if(ls){ 
                    let html = (jb.liabilities||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${createAccountLink(r)}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join('');
                    html += `<tr style="font-weight:600"><td colspan="2" style="text-align:right">Total Liabilities</td><td class="num">${formatCurrency(jb.totalLiabilities)}</td></tr>`;
                    ls.innerHTML = html;
                }
                const es = document.getElementById('bs-equity'); 
                if(es){ 
                    let html = (jb.equity||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${createAccountLink(r)}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join('');
                    html += `<tr style="font-weight:600"><td colspan="2" style="text-align:right">Total Equity</td><td class="num">${formatCurrency(jb.totalEquity)}</td></tr>`;
                    html += `<tr><td colspan="2" style="text-align:right;font-style:italic">Net Income (Current Year)</td><td class="num">${formatCurrency(jb.netIncome)}</td></tr>`;
                    html += `<tr style="font-weight:600"><td colspan="2" style="text-align:right">Total Equity & Net Income</td><td class="num">${formatCurrency(jb.totalEquityPlusNetIncome)}</td></tr>`;
                    html += `<tr style="font-weight:700;background:#f8fafc"><td colspan="2" style="text-align:right">Total Liabilities + Equity</td><td class="num">${formatCurrency(jb.totalLiabilitiesAndEquity)}</td></tr>`;
                    es.innerHTML = html;
                }

                // Update Balance Sheet balanced status
                const bsHeader = document.querySelector('.portal-card:has(#bs-assets) .portal-card-title');
                if(bsHeader){
                    const badge = jb.balanced ? '<span class="badge-pill paid">Balanced</span>' : '<span class="badge-pill overdue">Out of balance</span>';
                    bsHeader.innerHTML = `Balance Sheet ${badge}`;
                }
            }
            const i = await fetch('/api/accounting-reports/income-statement',{cache:'no-store'});
            if(i.ok){
                const ji = await i.json();
                const rev = document.getElementById('is-revenue'); 
                if(rev){ 
                    let html = (ji.revenue||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${createAccountLink(r)}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join('');
                    html += `<tr style="font-weight:600"><td colspan="2" style="text-align:right">Total Revenue</td><td class="num">${formatCurrency(ji.totalRevenue)}</td></tr>`;
                    rev.innerHTML = html;
                }
                const exp = document.getElementById('is-expenses'); 
                if(exp){ 
                    let html = (ji.expenses||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${createAccountLink(r)}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join('');
                    html += `<tr style="font-weight:600"><td colspan="2" style="text-align:right">Total Expenses</td><td class="num">${formatCurrency(ji.totalExpenses)}</td></tr>`;
                    html += `<tr style="font-weight:700;background:#f0fdf4"><td colspan="2" style="text-align:right">Net Income</td><td class="num">${formatCurrency(ji.netIncome)}</td></tr>`;
                    exp.innerHTML = html;
                }
            }
        }catch(e){ console.error('Accounting refresh failed', e); }
        finally{ setLoading(false); }
    }
    if(btn) btn.addEventListener('click', refresh);
})();
