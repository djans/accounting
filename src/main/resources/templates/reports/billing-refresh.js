(function(){
    const btn = document.getElementById('billing-refresh');
    const spinner = document.getElementById('billing-spinner');
    const startInput = document.querySelector('input[name="startDate"]');
    const endInput = document.querySelector('input[name="endDate"]');

    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }

    function formatCurrency(v){ try{ const n = Number(v); return new Intl.NumberFormat(undefined, {style:'currency', currency:'CAD'}).format(isNaN(n)?0:n); }catch(e){return v;} }

    async function refresh(){
        setLoading(true);
        try{
            const start = startInput && startInput.value ? startInput.value : '';
            const end = endInput && endInput.value ? endInput.value : '';

            // revenue
            if(start && end){
                const r = await fetch(`/api/reports/revenue?startDate=${start}&endDate=${end}`);
                if(r.ok){ const jr = await r.json(); document.getElementById('totalRevenue').textContent = formatCurrency(jr.totalRevenue || 0); document.getElementById('totalPaid').textContent = formatCurrency(jr.totalPaid || 0); document.getElementById('outstanding').textContent = formatCurrency(jr.totalOutstanding || 0); document.getElementById('invoiceCount').textContent = jr.invoiceCount || 0; }

                const t = await fetch(`/api/reports/tax-summary?startDate=${start}&endDate=${end}`);
                if(t.ok){ const jt = await t.json(); document.getElementById('totalGst').textContent = formatCurrency(jt.totalGst || 0); document.getElementById('totalQst').textContent = formatCurrency(jt.totalQst || 0); document.getElementById('totalHst').textContent = formatCurrency(jt.totalHst || 0); document.getElementById('totalTax').textContent = formatCurrency(jt.totalTax || 0); }
            }

            // status summary
            const s = await fetch('/api/reports/invoice-status-summary');
            if(s.ok){ const js = await s.json(); ['draft','sent','viewed','partiallyPaid','paid','overdue','cancelled','refunded'].forEach(k=>{ const el = document.getElementById('status-'+k); if(el) el.textContent = js[k] || 0; }); }

        }catch(e){ console.error('Billing refresh failed', e); }
        finally{ setLoading(false); }
    }

    if(btn) btn.addEventListener('click', refresh);
})();