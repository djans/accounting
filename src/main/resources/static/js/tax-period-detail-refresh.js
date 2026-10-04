(function(){
    const btn = document.getElementById('period-refresh');
    const spinner = document.getElementById('period-spinner');
    const idMatch = window.location.pathname.match(/\/tax\/periods\/(\d+)/);
    const collectedEl = document.getElementById('period-tax-collected');
    const itcEl = document.getElementById('period-itc');
    const netEl = document.getElementById('period-net');
    const collectedTbody = document.querySelector('#table-collected tbody');
    const itcTbody = document.querySelector('#table-itc tbody');
    const returnRowsTbody = document.getElementById('table-return-lines-body');
    const returnLineWarning = document.getElementById('returnLineMappingWarning');
    const fileReturnButton = document.getElementById('fileReturnButton');

    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function fmt(v){ try{ return new Intl.NumberFormat(undefined,{style:'currency',currency:'CAD'}).format(v); }catch(e){ return v; } }
    function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]; }); }

    async function refresh(){ 
        if(!idMatch) return; 
        setLoading(true); 
        try{ 
            const id = idMatch[1]; 
            const r = await fetch(`/api/tax/periods/${id}`,{cache:'no-store'}); 
            if(r.ok){ 
                const data = await r.json(); 
                const p = data.period || data; 
                if(collectedEl) collectedEl.textContent = fmt(p.taxCollected || 0); 
                if(itcEl) itcEl.textContent = fmt(p.taxItc || 0); 
                if(netEl) netEl.textContent = fmt(p.netOwing || 0); 

                if (data.collectedDetail && collectedTbody) {
                    collectedTbody.innerHTML = data.collectedDetail.length > 0 
                        ? data.collectedDetail.map(d => `<tr><td>${escapeHtml(d.date)}</td><td><a href="/invoices/${d.id}">${escapeHtml(d.number)}</a></td><td>${escapeHtml(d.entityName)}</td><td class="num">${fmt(d.amount)}</td></tr>`).join('')
                        : '<tr><td colspan="4" class="text-center text-muted">Aucune taxe collectée pour cette période.</td></tr>';
                }
                if (data.itcDetail && itcTbody) {
                    itcTbody.innerHTML = data.itcDetail.length > 0
                        ? data.itcDetail.map(d => `<tr><td>${escapeHtml(d.date)}</td><td><a href="/bills/${d.id}">${escapeHtml(d.number)}</a></td><td>${escapeHtml(d.entityName)}</td><td class="num">${fmt(d.amount)}</td></tr>`).join('')
                        : '<tr><td colspan="4" class="text-center text-muted">Aucun ITC pour cette période.</td></tr>';
                }
                if (data.taxReturnRows && returnRowsTbody) {
                    returnRowsTbody.innerHTML = data.taxReturnRows.map(row => {
                        const classes = row.total ? 'table-secondary fw-bold' : (row.unmapped ? 'table-warning' : '');
                        const amount = row.amount == null ? '' : fmt(row.amount);
                        const balance = row.balance == null ? '' : fmt(row.balance);
                        return `<tr class="${classes}"><td>${escapeHtml(row.description)}</td><td>${escapeHtml(row.line)}</td><td class="num">${amount}</td><td class="num">${balance}</td></tr>`;
                    }).join('');
                    if(returnLineWarning) {
                        returnLineWarning.classList.toggle('d-none', !data.hasUnmappedReturnLineAmounts);
                    }
                    if(fileReturnButton) fileReturnButton.disabled = Boolean(data.hasUnmappedReturnLineAmounts);
                }
            } 
        }catch(e){ console.error('period refresh failed', e);} finally{ setLoading(false);} 
    }
    if(btn) btn.addEventListener('click', refresh);
})();