Attribute VB_Name = "AzquoMaintenance"
'This code, and all code associated with the Azquo workbook, copyright Azquo Ltd 2013.  Programmed by Bill Cawley

Option Explicit



Public az_RgtClkMenu As CommandBar
Public azName As Object







Function EncodeBase64(sString As String) As String




Const clOneMask = 16515072          '000000 111111 111111 111111
Const clTwoMask = 258048            '111111 000000 111111 111111
Const clThreeMask = 4032            '111111 111111 000000 111111
Const clFourMask = 63               '111111 111111 111111 000000

Const clHighMask = 16711680         '11111111 00000000 00000000
Const clMidMask = 65280             '00000000 11111111 00000000
Const clLowMask = 255               '00000000 00000000 11111111

Const cl2Exp18 = 262144             '2 to the 18th power
Const cl2Exp12 = 4096               '2 to the 12th
Const cl2Exp6 = 64                  '2 to the 6th
Const cl2Exp8 = 256                 '2 to the 8th
Const cl2Exp16 = 65536              '2 to the 16th


    Dim bTrans(63) As Byte, lPowers8(255) As Long, lPowers16(255) As Long, bOut() As Byte, bIn() As Byte
    Dim lChar As Long, lTrip As Long, iPad As Integer, lLen As Long, lTemp As Long, lPos As Long, lOutSize As Long

    For lTemp = 0 To 63                                 'Fill the translation table.
        Select Case lTemp
            Case 0 To 25
                bTrans(lTemp) = 65 + lTemp              'A - Z
            Case 26 To 51
                bTrans(lTemp) = 71 + lTemp              'a - z
            Case 52 To 61
                bTrans(lTemp) = lTemp - 4               '1 - 0
            Case 62
                bTrans(lTemp) = 43                      'Chr(43) = "+"
            Case 63
                bTrans(lTemp) = 47                      'Chr(47) = "/"
        End Select
    Next lTemp

    For lTemp = 0 To 255                                'Fill the 2^8 and 2^16 lookup tables.
        lPowers8(lTemp) = lTemp * cl2Exp8
        lPowers16(lTemp) = lTemp * cl2Exp16
    Next lTemp

    iPad = Len(sString) Mod 3                           'See if the length is divisible by 3
    If iPad Then                                        'If not, figure out the end pad and resize the input.
        iPad = 3 - iPad
        sString = sString & String(iPad, Chr(0))
    End If

    bIn = StrConv(sString, vbFromUnicode)               'Load the input string.
    lLen = ((UBound(bIn) + 1) \ 3) * 4                  'Length of resulting string.
    lTemp = lLen \ 72                                   'Added space for vbCrLfs.
    lOutSize = ((lTemp * 2) + lLen) - 1                 'Calculate the size of the output buffer.
    ReDim bOut(lOutSize)                                'Make the output buffer.

    lLen = 0                                            'Reusing this one, so reset it.

    For lChar = LBound(bIn) To UBound(bIn) Step 3
        lTrip = lPowers16(bIn(lChar)) + lPowers8(bIn(lChar + 1)) + bIn(lChar + 2)    'Combine the 3 bytes
        lTemp = lTrip And clOneMask                     'Mask for the first 6 bits
        bOut(lPos) = bTrans(lTemp \ cl2Exp18)           'Shift it down to the low 6 bits and get the value
        lTemp = lTrip And clTwoMask                     'Mask for the second set.
        bOut(lPos + 1) = bTrans(lTemp \ cl2Exp12)       'Shift it down and translate.
        lTemp = lTrip And clThreeMask                   'Mask for the third set.
        bOut(lPos + 2) = bTrans(lTemp \ cl2Exp6)        'Shift it down and translate.
        bOut(lPos + 3) = bTrans(lTrip And clFourMask)   'Mask for the low set.
        If lLen = 68 Then                               'Ready for a newline
            bOut(lPos + 4) = 13                         'Chr(13) = vbCr
            bOut(lPos + 5) = 10                         'Chr(10) = vbLf
            lLen = 0                                    'Reset the counter
            lPos = lPos + 6
        Else
            lLen = lLen + 4
            lPos = lPos + 4
        End If
    Next lChar

    If bOut(lOutSize) = 10 Then lOutSize = lOutSize - 2 'Shift the padding chars down if it ends with CrLf.

    If iPad = 1 Then                                    'Add the padding chars if any.
        bOut(lOutSize) = 61                             'Chr(61) = "="
    ElseIf iPad = 2 Then
        bOut(lOutSize) = 61
        bOut(lOutSize - 1) = 61
    End If

    EncodeBase64 = StrConv(bOut, vbUnicode)                 'Convert back to a string and return it.

End Function



Sub SearchData()
  'CheckConnectionId
  Dim azSearchName As String
  azSearchName = Range("az_SearchName")
   azResponse = AzquoPost("Name", """operation"":""structure"",""name"":""" & azSearchName & """")
   If (azResponse > "") Then
      azSearchNames.UserForm_Initialize
      azSearchNames.Show
   End If
   Range("az_currentSearch") = azNameChosen
   If azNameChosen > "" Then
      azResponse = AzquoPost("Value", "searchbynames=" & URLEncode(azNameChosen))
      Call FillTheRange(azResponse, "azSearchData")
   End If
   
   
End Sub







Sub register()
    Dim Name, Email, emailConfirmation, Business, Address1, Address2, Address3, Address4 As String
    Dim Postcode, Telephone, Password, passwordConfirmation, Keysent, Parameters, result As String
    
    Name = Range("signonName")
    Email = Range("signonEmail")
    emailConfirmation = Range("signonEmailConfirm")
    Business = Range("signonBusiness")
    Address1 = Range("signonAddress1")
    Address2 = Range("signonAddress2")
    Address3 = Range("signonAddress3")
    Address4 = Range("signonAddress4")
    Postcode = Range("signonPostcode")
    Telephone = Range("signonTelephone")
    Password = Range("signonPassword")
    passwordConfirmation = Range("signonPasswordConfirm")
    Keysent = Range("signonKey")
    
    If Name = "" Then
       MsgBox ("Please enter your name")
       End
    End If
    If Email = "" Then
       MsgBox ("Please enter a valid email")
       End
    End If
    If Email <> emailConfirmation Then
       MsgBox ("Your email addresses do not match")
       End
    End If
    If (Address1 = "") Then
       MsgBox ("Please enter the top line of your address")
       End
    End If
    If Postcode = "" Then
       MsgBox ("Please enter your postcode")
       End
    End If
    If Password = "" Then
       MsgBox ("Please enter a password")
       End
    End If
    If Password <> passwordConfirmation Then
       MsgBox ("Your passwords do not match")
       End
    End If
    Parameters = "op=signon&username=" & URLEncode(Name) & _
                 "&email=" & URLEncode(Email) & _
                 "&businessname=" & URLEncode(Business) & _
                 "&address1=" & URLEncode(Address1) & _
                 "&address2=" & URLEncode(Address2) & _
                 "&address3=" & URLEncode(Address3) & _
                 "&address4=" & URLEncode(Address4) & _
                 "&postcode=" & URLEncode(Postcode) & _
                 "&password=" & URLEncode(Password) & _
                 "&telephone=" & URLEncode(Telephone) & _
                 "&key=" & URLEncode(Keysent)
    result = AzquoPost1("Maintain", Parameters)
    If Keysent = "" Then
       Range("signonKey").EntireRow.Hidden = False
    Else
       Range("signonThankyou").EntireRow.Hidden = False
       azConnectionId = result
       'ZapSheet ("Introduction")
    End If
    

End Sub


Function CreateJson(Listname As String, RangeName As String)
    Dim JSON As String
    Dim Start As Range
    Dim RowNo, ColNo As Integer
    
    If Not Listname = "" Then
        JSON = "{""" & Listname & """:["
        Else
        JSON = "["
        End If
        
    Set Start = Range(RangeName)
    RowNo = 1
    While Start.Offset(RowNo, 1) > 0
       If RowNo > 1 Then
          JSON = JSON & ","
      End If
      JSON = JSON & "{"
       ColNo = 0
       While Start.Offset(0, ColNo) > ""
          If ColNo > 0 Then
             JSON = JSON & ","
          End If
          If InStr(Start.Offset(0, ColNo), "date") Or InStr(Start.Offset(0, ColNo), "Date") Or InStr(Start.Offset(0, ColNo), "DATE") Then
          ' format date in a way java likes
          JSON = JSON & """" & Start.Offset(0, ColNo) & """:""" & Format(DateValue(Start.Offset(RowNo, ColNo)), "yyyy-mm-dd") & """"
          Else
          ' normal
           
          JSON = JSON & """" & Start.Offset(0, ColNo) & """:""" & Start.Offset(RowNo, ColNo) & """"
          End If
          
          ColNo = ColNo + 1
       Wend
       RowNo = RowNo + 1
       JSON = JSON & "}"
     Wend
    If Not Listname = "" Then
     JSON = JSON & "]}"
        Else
     JSON = JSON & "]"
        End If
     CreateJson = JSON
    
End Function


Sub FillJSONRange(RangeName As String, response As String)
If Not response = "" Then
  Dim lib As New JSONLib
  Dim JSON As Object
  Dim oneline As Object
  Dim Count As Integer
  Dim RowNo, ColNo As Integer
  
  RowNo = Range(RangeName).Row
  Count = 1
  While Cells(RowNo + Count, 2) > ""
    Count = Count + 1
  Wend
  
  If Count > 1 Then
    Rows((RowNo + 1) & ":" & (RowNo + Count - 1)).EntireRow.Delete
  End If
  Count = 0
  Set JSON = lib.parse(response)
  For Each oneline In JSON
     Count = Count + 1
    Rows(RowNo + 1).EntireRow.Copy
     Rows(RowNo + Count).EntireRow.Insert
     Rows(RowNo + Count).EntireRow.PasteSpecial
     ColNo = 1
     While Cells(RowNo, ColNo) > "" And Cells(RowNo, ColNo) <> "password"
'       Cells(RowNo + Count, ColNo) = oneline(Cells(RowNo, ColNo).value)
       Cells(RowNo + Count, ColNo) = oneline.Item(Cells(RowNo, ColNo))
       ColNo = ColNo + 1
     Wend
     
  Next
  Application.CutCopyMode = False
  
  Cells(RowNo + 1, 1).Select
  End If
End Sub

Sub ActivateDatabases()
   Sheets("Database maintenance").Activate
End Sub

Sub ActivateAccess()
   Sheets("Access maintenance").Activate
End Sub

Sub ActivateUpload()
   Sheets("Upload files").Activate
End Sub
Sub DownloadDatabaseList()
   Dim result As String
   result = AzquoPost("Maintain", "op=databaselist")
   Call FillJSONRange("azDatabaseHeadings", result)
End Sub

Sub DownloadPermissionsList()
   Dim result As String
   result = AzquoPost("Maintain", "op=permissionlist")
   Call FillJSONRange("azPermissionsHeadings", result)
End Sub

Sub DownloadUserList()
   Dim result As String
   result = AzquoPost("Maintain", "op=userlist")
   Call FillJSONRange("azUserHeadings", result)
End Sub


Sub DownloadLogonList()
   Dim result As String
   result = AzquoPost("Maintain", "op=logonlist")
   Call FillJSONRange("azLogonHeadings", result)
End Sub

Sub SavePermissions()
    azResponse = AzquoPost("Maintain", "op=savepermissions&json=" & CreateJson("", "azPermissionsHeadings"))
     DownloadPermissionsList
End Sub

Sub SaveUsers()
     Dim Thing As String
     azResponse = AzquoPost("Maintain", "op=saveusers&json=" & CreateJson("", "azUserHeadings"))
     DownloadUserList
End Sub

Sub NewDatabase()
  
   azResponse = AzquoPost("Maintain", "op=newdatabase&database=" & Range("az_NewDatabase"))
   DownloadDatabaseList
End Sub

Sub BackupDatabase()
  Dim SourceName, TargetName, SummaryLevel As String
  SourceName = Range("SourceDatabase")
  TargetName = Range("TargetDatabase")
  SummaryLevel = Range("SummaryLevel")
  azResponse = AzquoPost("Maintain", "op=backupdatabase&source=" & SourceName & " & target=" & TargetName & "&summarylevel=" & SummaryLevel)
  DownloadDatabaseList
End Sub

Sub TruncateDatabase()
 Dim DatabaseName, StartDate As String
  DatabaseName = Range("TruncateDatabase")
  StartDate = Format(StartDate, "DD/MM/YY")
  If !MsgBox("Are you sure that you want to truncate " & DatabaseName & " before " & StartDate) Then
    End
  End If
  azResponse = AzquoPost("Maintain", "op=truncatedatabase&database=" & DatabaseName & "&startdate=" & StartDate)
  DownloadDatabaseList
End Sub

Sub DeleteDatabase()
  Dim Database As String
  Database = Range("DeleteDatabase")
  If !MsgBox("Are you sure that you want to delete " & Database, YesNo) Then
     End
  End If
  azResponse = AzquoPost("Maintain", "op=deletedatabase&database=" & DatabaseName)
  DownloadDatabaseList
End Sub


Sub SelectUploadFile()
    Dim FileName As String
    FileName = Application.GetOpenFilename
    #If Mac Then
        FileName = Replace(FileName, ":", "/")
        FileName = Replace(FileName, "Macintosh HD", "")
     #End If
    If (FileName > "") Then
       Range("az_UploadFilename") = FileName
    End If

End Sub

Function ReadFile(FileName As String) As String

  Dim f As Integer
  f = FreeFile()
  Open FileName For Binary Access Read As #f
    ReadFile = Space(FileLen(FileName))
    Get #f, , ReadFile
  Close #f
  
End Function


Sub CleanDatabase()
   If MsgBox("This will delete any unused name.  Are you sure?", vbYesNo) = vbYes Then
 
      azResponse = AzquoPost("Name", """operation"":""delete"",""name"":""all""")
  End If

End Sub

Sub UploadFiles()
Dim az_Parameters, az_response As String
Dim FileName As String
FileName = Range("az_UploadFilename")
While InStr(FileName, "\") > 0
    FileName = Mid(FileName, InStr(FileName, "\") + 1, 1000)
Wend


az_Parameters = "filename=" & FileName & _
                "&filetype=" & Range("az_UploadFiletype") & _
                "&language=" & Range("az_ImportLanguage") & _
                "&create=true"
  CheckConnectionId
  az_response = AzquoPostMulti("Import", az_Parameters, Range("az_UploadFilename"))
  DownloadUploadRecord
 
 End Sub
 
 Sub DownloadUploadRecord()
    Dim azResponse As String
    azResponse = AzquoPost("Maintain", "op=uploadslist")
    Call FillJSONRange("azUploadHeadings", azResponse)
 End Sub


Sub rtclkEdit()
  azSearchNames.mcEdit
End Sub

Sub rtclkCut()
  azSearchNames.mcCut
End Sub

Sub rtclkCopy()
  azSearchNames.mcCopy
End Sub

Sub rtclkPaste()
  azSearchNames.mcPaste
End Sub

Sub rtclkAddPeer()
 azSearchNames.cmdAddSibling_Click
End Sub

Sub rtclkAddChild()
 azSearchNames.cmdAddSibling_Click
End Sub

Sub rtclkDeleteName()
  azSearchNames.cmdRemoveNode_Click
End Sub
