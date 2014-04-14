VERSION 5.00
Begin {C62A69F0-16DC-11CE-9E98-00AA00574A4F} LogonForm 
   Caption         =   "Log on to Azquo database"
   ClientHeight    =   2025
   ClientLeft      =   45
   ClientTop       =   375
   ClientWidth     =   3795
   OleObjectBlob   =   "LogonForm.frx":0000
   StartUpPosition =   1  'CenterOwner
End
Attribute VB_Name = "LogonForm"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False





Private Sub LogonSubmit_Click()
    azConnectionId = "aborted"
    
    Dim params As String
    'test code....
     params = "useremail=" & UserName & "&password=" & Password & "&database=" & Database
    azConnectionId = AzquoPost1("Login", params)
    If (azConnectionId = "false") Then
       azConnectionId = ""
    End If
    If (azConnectionId > "") Then
       az_Logon = UserName
       az_Password = Password
       LogonForm.Hide
    Else
       MsgBox ("Please try again")
    End If
End Sub

Private Sub UserForm_Activate()
   UserName = az_Logon
   Password = az_Password
   Database = RangeInThisSheet("az_Database")
   If UserName > "" And Password > "" And Database > "" Then
        params = "useremail=" & UserName & "&password=" & Password & "&database=" & Database
        azConnectionId = AzquoPost1("Login", params)
        
        If azConnectionId > "" Then
          LogonForm.Hide
        End If
   End If

End Sub

