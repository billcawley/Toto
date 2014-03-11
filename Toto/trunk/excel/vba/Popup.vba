VERSION 1.0 CLASS
BEGIN
  MultiUse = -1  'True
END
Attribute VB_Name = "Popup"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = False
Attribute VB_Exposed = False
Option Explicit

Private Sub Class_Initialize()
   'MsgBox ("Starting")
End Sub

Private Sub Class_Terminate()
  'MsgBox ("Ending")
End Sub
Public Function CreateSubMenu(DuringMove As Boolean) As CommandBar
'' Originally Written  : 27-Mar-2001 by Andy Wiggins, Byg Software Limited
''  Modified by Bill Cawley for Azquo Jan 2014

Const lcon_PuName = "RightClickMenu"

''Create some objects
Dim cb As CommandBar
Dim cbc As CommandBarControl

    ''Ensure our popup menu does not exist
     
     DeleteCommandBar lcon_PuName
    Set cb = CommandBars.Add(Name:=lcon_PuName, Position:=msoBarPopup, MenuBar:=False, Temporary:=False)
      Set cbc = cb.Controls.Add
    With cbc
        .caption = "&Edit"
        .OnAction = "rtclkEdit"
    End With
        
    If DuringMove Then
        Set cbc = cb.Controls.Add
        With cbc
           .caption = "&Paste"
           .OnAction = "rtclkPaste"
        End With
    Else
        Set cbc = cb.Controls.Add
        With cbc
           .caption = "Cu&t"
           .OnAction = "rtclkCut"
        End With
        Set cbc = cb.Controls.Add
        With cbc
           .caption = "&Copy"
           .OnAction = "rtclkCopy"
        End With
    End If
    
    Set cbc = cb.Controls.Add
    With cbc
        .caption = "Add &peer"
        .OnAction = "rtclkAddPeer"
    End With

    Set cbc = cb.Controls.Add
    With cbc
        .caption = "Add &Child"
        .OnAction = "rtClkAddChild"
    End With
     Set cbc = cb.Controls.Add
    With cbc
        .caption = "&Delete"
        .OnAction = "rtClkDeleteName"
    End With
   
    Set CreateSubMenu = cb

End Function


Public Sub DeleteCommandBar(menuName)
Dim mb
    For Each mb In CommandBars
        If mb.Name = menuName Then
            CommandBars(menuName).Delete
        End If
    Next
End Sub

