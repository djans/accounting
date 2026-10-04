(function(){
    const btn = document.getElementById('billing-refresh');
    const spinner = document.getElementById('billing-spinner');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function formatCurrency(v){ try { return new Intl.NumberFormat(undefined, {style:'currency', currency:'CAD'}).format(Number(v) || 0); } catch(e) { return v; } }
    async function refresh(){
        setLoading(true);
        try {
            const a = await fetch('/api/reports/aging', {cache:'no-store'});
            if (a.ok) {
                const data = await a.json();
                [['aging-current','current'],['aging-30','30Days'],['aging-60','60Days'],
                 ['aging-61-90','61To90Days'],['aging-90','90DaysPlus'],['aging-total','totalOutstanding']]
                    .forEach(([id,key]) => { const el = document.getElementById(id); if (el) el.textContent = formatCurrency(data[key]); });
            }
        } catch (e) {
            console.error('Billing refresh failed', e);
        } finally {
            setLoading(false);
        }
    }
    if (btn) btn.addEventListener('click', refresh);
})();
