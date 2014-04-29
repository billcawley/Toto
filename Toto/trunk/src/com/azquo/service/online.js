var selectionX;
var selectionY;



selectionX = 0;
selectionY = 0;
positionSelection();


function positionSelection(){
    var cursor = document.getElementById("selector")
    var selectionCell = document.getElementById("cell" + selectionX + "-" + selectionY);
    cursor.style.left = selectionCell.style.left + "px";
    cursor.style.top = selectionCell.style.top + "px";
    cursor.style.width = selectionCell.style.width + "px";
    cursor.style.height = selectionCell.style.height + "px";

}


function keyDown(e){

    var unicode=e.charCode? e.charCode : e.keyCode
    switch(unicode) {
        case 37:
            if (selectionX > 0) selectionX--;
            break;
        case 38:
            if (selectionY > 0) selectionY--;
            break;
        case 39:
            var cell = getElementById("cell" + (selectionX + 1) + "-" + selectionY);
            if (cell != null) selectionX++;
            break;
        case 40:
            var cell = getElementById("cell" + selectionX + "-" + (selectionY + 1));
            if (cell != null) selectionY++;
            break;
    }
    positionSelection();


}