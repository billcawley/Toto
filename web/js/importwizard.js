const fileInput = document.querySelector('#file-js-example input[type=file]');
fileInput.onchange = () => {
    if (fileInput.files.length > 0) {
        const fileName = document.querySelector('#file-js-example .file-name');
        if (fileInput.files.length > 1) {
            fileName.textContent = "Multiple files selected";
        } else {
            fileName.textContent = fileInput.files[0].name;
        }
    }
}






function selectionChange(fieldname){
            var id = document.getElementById(fieldname);
            document.getElementById("pathSelected").value = fieldname + "|" + id.value;
    }
