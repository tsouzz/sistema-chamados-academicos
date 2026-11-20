document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-char-counter]').forEach((campo) => {
        const max = parseInt(campo.getAttribute('maxlength'), 10);
        const alvoId = campo.getAttribute('data-char-counter');
        const contador = document.getElementById(alvoId);
        if (!max || !contador) return;

        const atualizar = () => {
            const atual = campo.value.length;
            const restante = max - atual;
            contador.textContent = `${atual}/${max} caracteres (${restante} restantes)`;
            contador.classList.toggle('text-danger', restante <= 20);
            contador.classList.toggle('text-secondary', restante > 20);
        };

        campo.addEventListener('input', atualizar);
        atualizar();
    });

    const MAX_BYTES = 10 * 1024 * 1024;
    document.querySelectorAll('input[type="file"]').forEach((input) => {
        input.addEventListener('change', () => {
            const arquivos = Array.from(input.files || []);
            const excedido = arquivos.find((f) => f.size > MAX_BYTES);
            if (excedido) {
                alert(`O arquivo "${excedido.name}" ultrapassa o limite máximo permitido de 10 MB.`);
                input.value = '';
            }
        });
    });

    const selectStatus = document.getElementById('status');
    const campoObs = document.getElementById('observacaoAtendimento');
    const avisoObs = document.getElementById('avisoObsObrigatoria');

    if (selectStatus && campoObs && avisoObs) {
        const verificarObrigatoriedadeObs = () => {
            const statusFinal = selectStatus.value === 'CONCLUIDO' || selectStatus.value === 'CANCELADO';
            avisoObs.classList.toggle('d-none', !statusFinal);
            campoObs.required = statusFinal;
        };
        selectStatus.addEventListener('change', verificarObrigatoriedadeObs);
        verificarObrigatoriedadeObs();
    }

    document.querySelectorAll('form[data-validate="true"]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
                form.classList.add('was-validated');
                const primeiroInvalido = form.querySelector(':invalid');
                if (primeiroInvalido) {
                    primeiroInvalido.focus();
                }
                return;
            }

            const btnSubmit = form.querySelector('button[type="submit"]');
            if (btnSubmit) {
                btnSubmit.disabled = true;
                btnSubmit.dataset.originalText = btnSubmit.textContent;
                btnSubmit.textContent = 'Processando...';
            }
        });
    });
});