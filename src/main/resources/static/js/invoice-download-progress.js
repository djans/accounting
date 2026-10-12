document.addEventListener('DOMContentLoaded', function () {
    function setPending(control, pending, disableButton) {
        control.classList.toggle('is-download-pending', pending);
        control.setAttribute('aria-busy', String(pending));
        const spinner = control.querySelector('[data-download-spinner]');
        if (spinner) {
            spinner.hidden = !pending;
        }
        if (control instanceof HTMLButtonElement && disableButton !== false) {
            control.disabled = pending;
        }
        if (control instanceof HTMLAnchorElement) {
            if (pending) {
                control.setAttribute('aria-disabled', 'true');
            } else {
                control.removeAttribute('aria-disabled');
            }
        }
    }

    function showError(control, message) {
        const alert = document.createElement('div');
        alert.className = 'flash flash-error';
        alert.setAttribute('role', 'alert');
        alert.textContent = message;
        const content = control.closest('.portal-content') || document.body;
        content.prepend(alert);
    }

    function filenameFrom(response) {
        const disposition = response.headers.get('Content-Disposition') || '';
        const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i);
        if (encodedName) {
            const value = encodedName[1].trim().replace(/^"|"$/g, '');
            try {
                return decodeURIComponent(value);
            } catch (error) {
                if (!(error instanceof URIError)) {
                    throw error;
                }
                return value;
            }
        }
        const plainName = disposition.match(/filename="?([^";]+)"?/i);
        return plainName ? plainName[1].trim() : 'download';
    }

    async function consumeResponse(control, response) {
        if (!response.ok) {
            const message = (await response.text()).trim();
            throw new Error(message || 'The download could not be completed.');
        }

        const contentType = response.headers.get('Content-Type') || '';
        const disposition = response.headers.get('Content-Disposition') || '';
        if (/attachment/i.test(disposition)) {
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const downloadLink = document.createElement('a');
            downloadLink.href = url;
            downloadLink.download = filenameFrom(response);
            document.body.appendChild(downloadLink);
            downloadLink.click();
            downloadLink.remove();
            window.setTimeout(function () {
                URL.revokeObjectURL(url);
            }, 30000);
            return;
        }

        if (contentType.includes('text/html')) {
            const html = await response.text();
            if (response.url) {
                window.history.pushState({}, '', response.url);
            }
            document.open();
            document.write(html);
            document.close();
            return;
        }

        throw new Error('The server returned an unexpected download response.');
    }

    document.querySelectorAll('a[data-download-progress="fetch"]').forEach(function (link) {
        link.addEventListener('click', async function (event) {
            if (link.getAttribute('aria-busy') === 'true'
                    || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey
                    || link.target && link.target !== '_self') {
                return;
            }
            event.preventDefault();
            setPending(link, true);
            try {
                const response = await fetch(link.href, {
                    credentials: 'same-origin',
                    headers: { Accept: 'application/pdf, application/zip, text/html' }
                });
                await consumeResponse(link, response);
            } catch (error) {
                showError(link, error instanceof Error ? error.message : 'The download could not be completed.');
            } finally {
                setPending(link, false);
            }
        });
    });

    document.querySelectorAll('form[data-download-progress="fetch"]').forEach(function (form) {
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            const submitButton = event.submitter || form.querySelector('button[type="submit"]');
            const params = new URLSearchParams(new FormData(form));
            const method = form.method.toUpperCase();
            const url = new URL(form.action, window.location.href);
            const options = {
                method: method,
                credentials: 'same-origin',
                headers: { Accept: 'application/pdf, application/zip, text/html' }
            };
            if (method === 'GET') {
                url.search = params.toString();
            } else {
                options.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
                options.body = params.toString();
            }

            if (submitButton) {
                setPending(submitButton, true);
            }
            try {
                const response = await fetch(url, options);
                await consumeResponse(submitButton || form, response);
            } catch (error) {
                showError(submitButton || form,
                        error instanceof Error ? error.message : 'The download could not be completed.');
            } finally {
                if (submitButton) {
                    setPending(submitButton, false);
                }
            }
        });
    });

    document.querySelectorAll('form[data-download-progress="submit"]').forEach(function (form) {
        form.addEventListener('submit', function (event) {
            const submitButton = event.submitter || form.querySelector('button[type="submit"]');
            if (submitButton) {
                setPending(submitButton, true, false);
            }
        });
    });
});
