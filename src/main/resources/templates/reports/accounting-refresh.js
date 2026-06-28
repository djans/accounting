(function(){
    const btn = document.getElementById('acct-refresh');
    const spinner = document.getElementById('acct-spinner');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function formatCurrency(v){ try{ const n = Number(v); return new Intl.NumberFormat(undefined, {style:'currency', currency:'CAD'}).format(isNaN(n)?0:n); }catch(e){return v;} }

    async function refresh(){
        setLoading(true);
        try{
            // trial balance
            const t = await fetch('/api/accounting-reports/trial-balance', {cache:'no-store'});
            if(t.ok){ const jt = await t.json(); const tbody = document.getElementById('tb-tbody'); if(tbody){ tbody.innerHTML = Object.values(jt.accounts||{}).map(acct=>`<tr><td>${acct.accountNumber}</td><td>${acct.accountName}</td><td class='num'>${formatCurrency(acct.debitBalance)}</td><td class='num'>${formatCurrency(acct.creditBalance)}</td><td class='num'>${formatCurrency(acct.balance)}</td></tr>`).join(''); }
                const footDeb = document.querySelector('tfoot tr td:nth-child(4)'); const footCred = document.querySelector('tfoot tr td:nth-child(5)'); if(footDeb) footDeb.textContent = formatCurrency(jt.totalDebits||0); if(footCred) footCred.textContent = formatCurrency(jt.totalCredits||0);
            }
            // balance sheet
            const b = await fetch('/api/accounting-reports/balance-sheet',{cache:'no-store'});
            if(b.ok){ const jb = await b.json(); const as = document.getElementById('bs-assets'); if(as){ as.innerHTML = (jb.assets||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${r.accountName}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join(''); }
                const ls = document.getElementById('bs-liabilities'); if(ls){ ls.innerHTML = (jb.liabilities||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${r.accountName}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join(''); }
                const es = document.getElementById('bs-equity'); if(es){ es.innerHTML = (jb.equity||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${r.accountName}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join(''); }
                const totalAssets = document.querySelector('td[th\:text="${#numbers.formatCurrency(balanceSheet[\'totalAssets\'])}"]');
            }
            // income statement
            const i = await fetch('/api/accounting-reports/income-statement',{cache:'no-store'});
            if(i.ok){ const ji = await i.json(); const rev = document.getElementById('is-revenue'); if(rev){ rev.innerHTML = (ji.revenue||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${r.accountName}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join(''); }
                const exp = document.getElementById('is-expenses'); if(exp){ exp.innerHTML = (ji.expenses||[]).map(r=>`<tr><td>${r.accountNumber}</td><td>${r.accountName}</td><td class='num'>${formatCurrency(r.balance)}</td></tr>`).join(''); }
                const netEl = document.querySelector('td[th\:text="${#numbers.formatCurrency(incomeStatement[\'netIncome\'])}"]');
            }
        }catch(e){ console.error('Accounting refresh failed', e); }
        finally{ setLoading(false); }
    }
    if(btn) btn.addEventListener('click', refresh);
    setInterval(refresh,30000);
})();