VERSION 5.00
Begin {C62A69F0-16DC-11CE-9E98-00AA00574A4F} SelectName 
   Caption         =   "Select Name"
   ClientHeight    =   5145
   ClientLeft      =   45
   ClientTop       =   345
   ClientWidth     =   4710
   OleObjectBlob   =   "SelectName.frx":0000
   StartUpPosition =   1  'CenterOwner
End
Attribute VB_Name = "SelectName"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False

Sub SelectName_Initialize()
   Dim lib As JSONLib
   Dim NameList, nName As Object
   
   NameChoice.Clear
   Set lib = New JSONLib
   Set NameList = lib.parse(azResponse)
   For Each nName In NameList.Item("names")
      NameChoice.AddItem (nName.Item("name"))
   Next
      
   
End Sub



Private Sub NameChoice_DblClick(ByVal Cancel As MSForms.ReturnBoolean)
  Call SelectButton_Click
End Sub


Private Sub SelectButton_Click()
  azNameChosen = NameChoice
  SelectName.Hide
End Sub
